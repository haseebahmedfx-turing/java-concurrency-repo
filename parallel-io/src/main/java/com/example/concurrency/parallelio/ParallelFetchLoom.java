package com.example.concurrency.parallelio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Virtual-threads (Loom) based parallel fetch demo.
 * IO-01: returns results sorted by latency and prints a success/failure summary.
 */
public class ParallelFetchLoom {

    /** Result of one fetch. */
    public record Result(String id, long millis, boolean simulated, int status) {
        public boolean success() {
            return simulated ? status == 0 : (status >= 200 && status < 300);
        }
    }

    private static Result simulateFetch(String id) {
        int baseMs = Math.abs(id.hashCode() % 150) + 50; // 50..199 ms (deterministic)
        long start = System.nanoTime();
        try {
            Thread.sleep(baseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long durMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new Result(id, durMs, true, 0);
    }

    private static Result realFetch(HttpClient http, String url) {
        long start = System.nanoTime();
        try {
            // Offline-safe "real" call: simulate latency and 200 status.
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
     * Runs the demo on virtual threads, sorts by latency (non-decreasing),
     * prints a summary, and returns results for tests.
     */
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
                    if (in.startsWith("sim")) return simulateFetch(in);
                    return realFetch(http, in);
                }));
            }

            List<Result> results = new ArrayList<>();
            for (Future<Result> f : futures) {
                results.add(f.get());
            }

            results.sort(Comparator.comparingLong(Result::millis));
            printSummary(results);
            return results;
        }
    }

    private static void printSummary(List<Result> results) {
        int ok = 0, fail = 0;
        for (Result r : results) {
            if (r.success()) ok++; else fail++;
        }
        System.out.println("[ParallelIO/Loom] summary: success=" + ok + " failure=" + fail + " total=" + results.size());
    }

    public static void main(String[] args) throws Exception {
        List<Result> r = run(args);
        for (Result res : r) {
            System.out.println(res.id() + " -> " + res.millis() + "ms, success=" + res.success());
        }
    }
}
