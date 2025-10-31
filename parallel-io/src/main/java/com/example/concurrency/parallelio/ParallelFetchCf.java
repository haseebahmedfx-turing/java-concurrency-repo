package com.example.concurrency.parallelio;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompletableFuture-based parallel fetch demo.
 * returns results sorted by latency and prints a success/failure summary.
 */
public class ParallelFetchCf {
    private static volatile int lastPeakConcurrency = 0;
    public static int lastPeakConcurrency() { return lastPeakConcurrency; }

    /** Result of one fetch. */
    public record Result(String id, long millis, boolean simulated, int status) {
        /** Success: simulated OK (status==0) or real HTTP 2xx. */
        public boolean success() {
            return simulated ? status == 0 : (status >= 200 && status < 300);
        }
    }

    /** Simulated fetch: sleeps a deterministic duration derived from the id. */
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

    /** "Real" fetch placeholder; for tests we keep it offline-safe. */
    private static Result realFetch(HttpClient http, String url) {
        long start = System.nanoTime();
        try {
            // Keep offline-safe: just simulate a stable delay and 200 status.
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
     * prints a summary, and returns the list for tests.
     */
    public static List<Result> run(String... args) throws Exception {
        List<String> inputs = (args != null && args.length > 0)
                ? Arrays.asList(args)
                : List.of("simA", "simB", "simC");

        int pool = Integer.getInteger("io.pool", 8);
        ExecutorService exec = Executors.newFixedThreadPool(pool);
                //Concurrency cap via Semaphore
        final int cap = Integer.getInteger("io.cf.cap", 8);
        final Semaphore gate = new Semaphore(Math.max(1, cap));
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(exec)
                    .build();

            List<CompletableFuture<Result>> futures = new ArrayList<>();
            for (String in : inputs) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        gate.acquire();
                        int now = inFlight.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        if (in.startsWith("sim")) return simulateFetch(in);
                    return realFetch(http, in);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new Result("interrupted", -1, true, 1);
                    } finally {
                        inFlight.decrementAndGet();
                        gate.release();
                    }
                }, exec));
            }

            List<Result> results = new ArrayList<>();
            for (CompletableFuture<Result> f : futures) {
                results.add(f.get());
            }

            results.sort(Comparator.comparingLong(Result::millis));
            lastPeakConcurrency = peak.get();
            System.out.println("[ParallelIO/CF] peakConcurrency=" + lastPeakConcurrency);
            printSummary(results);
            return results;
        } finally {
            exec.shutdownNow();
        }
    }

    /** Prints success/failure/total summary. */
    private static void printSummary(List<Result> results) {
        int ok = 0, fail = 0;
        for (Result r : results) {
            if (r.success()) ok++; else fail++;
        }
        System.out.println("[ParallelIO/CF] summary: success=" + ok + " failure=" + fail + " total=" + results.size());
    }

    public static void main(String[] args) throws Exception {
        List<Result> r = run(args);
        for (Result res : r) {
            System.out.println(res.id() + " -> " + res.millis() + "ms, success=" + res.success());
        }
    }
}
