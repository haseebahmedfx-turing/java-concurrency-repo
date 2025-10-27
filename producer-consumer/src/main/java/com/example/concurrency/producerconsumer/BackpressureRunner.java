package com.example.concurrency.producerconsumer;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Producer/Consumer runner demonstrating backpressure using a bounded queue and
 * a rejection handler that runs work on the caller thread.
 */
public class BackpressureRunner {

    /** Captures essential metrics for quick assertions. */
    public record Metrics(int produced, int consumed, int callerRuns, int rejected, int queueEnd) {}

    /** Execute a run with the given parameters. */
    public static Metrics run(int poolSize, int queueCapacity, int durationSec, int producerRatePerSec) {
        // Core executor with bounded queue
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        poolSize,
                        poolSize,
                        0L,
                        TimeUnit.MILLISECONDS,
                        workQueue,
                        new ThreadFactory() {
                            private final AtomicInteger idx = new AtomicInteger();
                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(r, "pc-worker-" + idx.incrementAndGet());
                                t.setDaemon(true);
                                return t;
                            }
                        });
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger callerRuns = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        // Backpressure strategy: run on caller thread when queue is full
        executor.setRejectedExecutionHandler((r, ex) -> {
            callerRuns.incrementAndGet();
            r.run();
        });

        Instant endAt = Instant.now().plusSeconds(durationSec);
        long nanosPerItem = (long) (1_000_000_000.0 / Math.max(1, producerRatePerSec));
        Random rnd = new Random();

        Thread producer =
                new Thread(
                        () -> {
                            long next = System.nanoTime();
                            while (Instant.now().isBefore(endAt)) {
                                try {
                                    produced.incrementAndGet();
                                    executor.execute(
                                            () -> {
                                                try {
                                                    Thread.sleep(2 + rnd.nextInt(8));
                                                    consumed.incrementAndGet();
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                }
                                            });
                                } catch (RejectedExecutionException rex) {
                                    rejected.incrementAndGet();
                                }
                                next += nanosPerItem;
                                long sleepNanos = next - System.nanoTime();
                                if (sleepNanos > 0) LockSupport.parkNanos(sleepNanos);
                                else Thread.yield();
                            }
                        },
                        "pc-producer");
        producer.setDaemon(true);
        producer.start();

        try {
            Thread.sleep(durationSec * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new Metrics(produced.get(), consumed.get(), callerRuns.get(), rejected.get(), workQueue.size());
    }
}
