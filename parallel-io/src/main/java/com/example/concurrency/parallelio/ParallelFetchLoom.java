package com.example.concurrency.parallelio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Virtual-threads (Loom) parallel fetch demo.
 * sorted results + summary; simulated retries.
 */
public class ParallelFetchLoom {

    public record Result(String id, long millis, boolean simulated, int status) {
        public boolean success() {
            return simulated ? status == 0 : (status >= 200 && status < 300);
        }
        @Override public String toString() {
            return id + " -> " + millis + "ms, success=" + success();
        }
    }

    // ---------- helpers ----------
    private static boolean isSimulated(String id) {
        return id != null && (id.startsWith("sim") || id.startsWith("flaky"));
    }
    private static int failuresBeforeSuccessFor(String id) {
        String perId = System.getProperty("io.sim.flaky.failures." + id);
        if (perId != null) {
            try { return Integer.parseInt(perId.trim()); } catch (NumberFormatException ignore) {}
        }
        int global = Integer.getInteger("io.sim.flaky.failures", 0);
        if (id != null && id.startsWith("flaky")) return global;
        return 0;
    }
    private static long retryBackoffMillis() {
        return Long.getLong("io.retry.backoff.millis", 50L);
    }

    private static Result simulateFetch(String id) {
        int baseMs = Math.abs(id.hashCode() % 150) + 50;
        long start = System.nanoTime();
        try { Thread.sleep(baseMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new Result(id, durMs, true, 0);
    }

    private static Result simulateFetchWithRetry(String id) {
        int toFail = failuresBeforeSuccessFor(id);
        long start = System.nanoTime();
        int failed = 0;
        while (true) {
            int baseMs = Math.abs(id.hashCode() % 150) + 50;
            try { Thread.sleep(baseMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return new Result(id, 0, true, 1); }
            if (failed < toFail) {
                failed++;
                try { Thread.sleep(retryBackoffMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                continue;
            }
            long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Result(id, durMs, true, 0);
        }
    }

    private static Result realFetch(HttpClient http, String url) {
        long start = System.nanoTime();
        try {
            Thread.sleep(100);
            long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Result(url, durMs, false, 200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Result(url, durMs, false, 500);
        }
    }

    public static List<Result> run(String... args) throws Exception {
        List<String> inputs = (args != null && args.length > 0)
                ? Arrays.asList(args)
                : List.of("simX", "simY", "simZ", "simW");

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(exec)
                    .build();

            List<Future<Result>> futures = new ArrayList<>();
            for (String in : inputs) {
                futures.add(exec.submit(() -> {
                    if (isSimulated(in)) {
                        int planned = failuresBeforeSuccessFor(in);
                        return (planned > 0) ? simulateFetchWithRetry(in) : simulateFetch(in);
                    }
                    return realFetch(http, in);
                }));
            }

            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) results.add(f.get());

            results.sort(Comparator.comparingLong(Result::millis));
            printSummary(results);
            return results;
        }
    }

    private static void printSummary(List<Result> results) {
        int ok = 0, fail = 0;
        for (Result r : results) if (r.success()) ok++; else fail++;
        System.out.println("[ParallelIO/Loom] summary: success=" + ok + " failure=" + fail + " total=" + results.size());
    }

    public static void main(String[] args) throws Exception {
        List<Result> r = run(args);
        for (Result res : r) System.out.println(res);
    }
}
