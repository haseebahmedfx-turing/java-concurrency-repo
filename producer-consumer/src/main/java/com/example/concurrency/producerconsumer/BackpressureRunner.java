package com.example.concurrency.producerconsumer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Producer/Consumer runner demonstrating backpressure using a bounded queue and
 * a rejection handler that runs work on the caller thread.
 */
public class BackpressureRunner {
    /** A single periodic snapshot of the executor state. */
    public record Sample(long epochMillis, int queueDepth, int activeWorkers, long completedTasks) {}

    /** Last run's samples exposed for tests (read-only copy). */
    private static volatile List<Sample> LAST_SAMPLES = List.of();

    /** Returns the last recorded samples for the most recent run. */
    public static List<Sample> lastSamples() {
        return LAST_SAMPLES;
    }

    /** Captures essential metrics for quick assertions. */
    public record Metrics(int produced, int consumed, int callerRuns, int rejected, int queueEnd, int samplesCount) {}

    /** Execute a run with the given parameters. */
    public static Metrics run(int poolSize, int queueCapacity, int durationSec, int producerRatePerSec) {
        //backoff controls
        final boolean backoffEnabled = Boolean.parseBoolean(System.getProperty("backoff.enabled", "true"));
        final double backoffThreshold = Double.parseDouble(System.getProperty("backoff.threshold", "0.25"));
        final long backoffNanos = Long.parseLong(System.getProperty("backoff.nanos", "200000"));
    
        //Rejection policy switch (-DrejectionPolicy=CallerRuns|Block|DropNewest, default=CallerRuns)
        final String rejectionPolicy = System.getProperty("rejectionPolicy", "CallerRuns");

        //Graceful drain flag from system property (-Ddrain=true)
        final boolean drain = Boolean.parseBoolean(System.getProperty("drain", "false"));

        // Core executor with bounded queue
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);

        // --- Metrics sampler: periodically capture queue depth, active workers, and completed tasks
        List<Sample> _samples = new CopyOnWriteArrayList<>();
        ScheduledExecutorService _sampler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-sampler");
            t.setDaemon(true);
            return t;
        });

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
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

        _sampler.scheduleAtFixedRate(() -> {
            try {
                int depth = workQueue.size();
                int active = executor.getActiveCount();
                long completed = executor.getCompletedTaskCount();
                _samples.add(new Sample(System.currentTimeMillis(), depth, active, completed));
            } catch (Throwable ignore) {
                // Keep sampling lightweight
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        AtomicInteger produced = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger callerRuns = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        // Backpressure strategy: run on caller thread when queue is full
        executor.setRejectedExecutionHandler((r, ex) -> {
            switch (rejectionPolicy.toLowerCase()) {
                case "callerruns":
                    callerRuns.incrementAndGet();
                    r.run();
                    break;
                case "dropnewest":
                    // Drop the incoming task; count as rejected
                    rejected.incrementAndGet();
                    // do nothing else
                    break;
                case "block":
                    // Block producer until space is available; do not increment rejected
                    try {
                        workQueue.put(r);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                default:
                    // Fallback to CallerRuns
                    callerRuns.incrementAndGet();
                    r.run();
            }
        });Instant endAt = Instant.now().plusSeconds(durationSec);
        long nanosPerItem = (long) (1_000_000_000.0 / Math.max(1, producerRatePerSec));
        Random rnd = new Random();

        Thread producer = new Thread(() -> {
            long next = System.nanoTime();
            while (Instant.now().isBefore(endAt)) {
                try {
                    produced.incrementAndGet();
                    executor.execute(() -> {
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

                
                //back off if callerRuns ratio crosses threshold
                if (backoffEnabled) {
                    int p = Math.max(1, produced.get());
                    double ratio = (double) callerRuns.get() / (double) p;
                    if (ratio >= backoffThreshold) {
                        java.util.concurrent.locks.LockSupport.parkNanos(backoffNanos);
                    }
                }
next += nanosPerItem;
                long sleepNanos = next - System.nanoTime();
                if (sleepNanos > 0) {
                    LockSupport.parkNanos(sleepNanos);
                } else {
                    Thread.yield();
                }
            }
        }, "pc-producer");

        producer.setDaemon(true);
        producer.start();

        try {
            Thread.sleep(durationSec * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();
        if (drain) {
            // Drain: wait until all tasks are completed
            try {
                while (!executor.isTerminated()) {
                    executor.awaitTermination(100, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            // No drain: return promptly; allow leftover tasks/queue to remain
            try {
                executor.awaitTermination(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Stop sampler and publish samples for tests
        _sampler.shutdownNow();
        LAST_SAMPLES = Collections.unmodifiableList(new ArrayList<>(_samples));

        // Optional: print simple summary
        if (!LAST_SAMPLES.isEmpty()) {
            Sample last = LAST_SAMPLES.get(LAST_SAMPLES.size() - 1);
            System.out.printf("Sampler: %d samples. Last={queue=%d, active=%d, completed=%d}%n",
                    LAST_SAMPLES.size(), last.queueDepth(), last.activeWorkers(), last.completedTasks());
        }

        return new Metrics(produced.get(), consumed.get(), callerRuns.get(), rejected.get(), workQueue.size(), LAST_SAMPLES.size());
    }
}
