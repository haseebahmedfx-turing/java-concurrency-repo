package com.example.concurrency.parallelio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CompletableFuture-based parallel fetch demo.
 * sorted results + summary
 * optional concurrency cap via -Dio.cf.cap
 * simulated retries/backoff (treat sim* and flaky* as simulated)
 */
public class ParallelFetchCf {

    /** Result of one fetch. */
    public record Result(String id, long millis, boolean simulated, int status) {
        /** Success: simulated OK (status==0) or real HTTP 2xx. */
        public boolean success() {
            return simulated ? status == 0 : (status >= 200 && status < 300);
        }
        @Override public String toString() {
            return id + " -> " + millis + "ms, success=" + success();
        }
    }

    // ---- visibility for tests ----
    private static volatile int lastPeakConcurrency = 0;
    public static int lastPeakConcurrency() { return lastPeakConcurrency; }

    // ---------- helpers (retries/backoff) ----------
    private static boolean isSimulated(String id) {
        // Treat both "sim*" and "flaky*" as simulated inputs.
        return id != null && (id.startsWith("sim") || id.startsWith("flaky"));
    }
    private static int failuresBeforeSuccessFor(String id) {
        // Per-id override: -Dio.sim.flaky.failures.<id>=N
        String perId = System.getProperty("io.sim.flaky.failures." + id);
        if (perId != null) {
            try { return Integer.parseInt(perId.trim()); } catch (NumberFormatException ignore) {}
        }
        // Global fallback used when id looks "flaky*"
        int global = Integer.getInteger("io.sim.flaky.failures", 0);
        if (id != null && id.startsWith("flaky")) return global;
        return 0;
    }
    private static long retryBackoffMillis() {
        return Long.getLong("io.retry.backoff.millis", 50L);
    }

    /** Simulated fetch (single attempt). */
    private static Result simulateFetch(String id) {
        int baseMs = Math.abs(id.hashCode() % 150) + 50; // 50..199 ms (deterministic)
        long start = System.nanoTime();
        try { Thread.sleep(baseMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new Result(id, durMs, true, 0);
    }

    /** Simulated fetch with retry/backoff: fail N times then succeed; duration includes backoffs. */
    private static Result simulateFetchWithRetry(String id) {
        int toFail = failuresBeforeSuccessFor(id);
        long start = System.nanoTime();
        int failed = 0;
        while (true) {
            int baseMs = Math.abs(id.hashCode() % 150) + 50;
            try { Thread.sleep(baseMs); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); return new Result(id, 0, true, 1); }
            if (failed < toFail) {
                failed++;
                try { Thread.sleep(retryBackoffMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                continue;
            }
            long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new Result(id, durMs, true, 0);
        }
    }

    /** "Real" fetch placeholder; kept offline-safe. */
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

    /**
     * Runs the demo, gathers results, sorts by latency (non-decreasing),
     * prints a summary, records peak concurrency, and returns results.
     */
    public static List<Result> run(String... args) throws Exception {
        List<String> inputs = (args != null && args.length > 0)
                ? Arrays.asList(args)
                : List.of("simA", "simB", "simC");

        int pool = Integer.getInteger("io.pool", 8);
        ExecutorService exec = Executors.newFixedThreadPool(pool);
        try {
            // IO-02: optional cap via -Dio.cf.cap
            Integer capProp = Integer.getInteger("io.cf.cap", null);
            final Semaphore gate = (capProp != null) ? new Semaphore(Math.max(1, capProp)) : null;

            // Track in-flight & peak regardless of cap
            final AtomicInteger inFlight = new AtomicInteger();
            final AtomicInteger peak = new AtomicInteger();

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(exec)
                    .build();

            List<CompletableFuture<Result>> futures = new ArrayList<>();
            for (String in : inputs) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        if (gate != null) gate.acquire();
                        int now = inFlight.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);

                        if (isSimulated(in)) {
                            int planned = failuresBeforeSuccessFor(in);
                            return (planned > 0) ? simulateFetchWithRetry(in) : simulateFetch(in);
                        }
                        return realFetch(http, in);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new Result("interrupted", -1, true, 1);
                    } finally {
                        inFlight.decrementAndGet();
                        if (gate != null) gate.release();
                    }
                }, exec));
            }

            List<Result> results = new ArrayList<>();
            for (CompletableFuture<Result> f : futures) {
                results.add(f.get());
            }

            results.sort(Comparator.comparingLong(Result::millis));
            printSummary(results);
            lastPeakConcurrency = peak.get(); // <- exposes to tests
            return results;
        } finally {
            exec.shutdownNow();
        }
    }

    /** Prints success/failure/total summary. */
    private static void printSummary(List<Result> results) {
        int ok = 0, fail = 0;
        for (Result r : results) if (r.success()) ok++; else fail++;
        System.out.println("[ParallelIO/CF] summary: success=" + ok + " failure=" + fail + " total=" + results.size());
    }

    public static void main(String[] args) throws Exception {
        List<Result> r = run(args);
        for (Result res : r) System.out.println(res);
    }
}
