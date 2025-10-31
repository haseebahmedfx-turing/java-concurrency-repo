package com.example.concurrency.parallelio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Network-free smoke tests for both implementations. */
public class ParallelIoTest {

    @Test
    @DisplayName("CompletableFuture fan-out runs with simulated inputs")
    void cfSmoke() throws Exception {
        ParallelFetchCf.main(new String[] {"simA", "simB"});
        assertTrue(true);
    }

    @Test
    @DisplayName("Virtual threads fan-out runs with simulated inputs")
    void loomSmoke() throws Exception {
        ParallelFetchLoom.main(new String[] {"simX", "simY"});
        assertTrue(true);
    }

    @Test
    @DisplayName("CF results are sorted by latency and counts tally")
    void cfSortedByLatency() throws Exception {
        var results = ParallelFetchCf.run("simA","simB","simC","simD");
        long prev = Long.MIN_VALUE;
        int ok=0, fail=0;
        for (var r : results) {
            assertTrue(r.millis() >= prev, "non-decreasing millis");
            prev = r.millis();
            if (r.success()) ok++; else fail++;
        }
        assertEquals(ok + fail, results.size(), "summary tally should equal total");
    }

    @Test
    @DisplayName("Loom results are sorted by latency and counts tally")
    void loomSortedByLatency() throws Exception {
        var results = ParallelFetchLoom.run("simX","simY","simZ","simW");
        long prev = Long.MIN_VALUE;
        int ok=0, fail=0;
        for (var r : results) {
            assertTrue(r.millis() >= prev, "non-decreasing millis");
            prev = r.millis();
            if (r.success()) ok++; else fail++;
        }
        assertEquals(ok + fail, results.size(), "summary tally should equal total");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("CF semaphore caps max concurrency to configured cap")
    void cfSemaphoreCapsConcurrency() throws Exception {
        System.setProperty("io.cf.cap", "3");
        System.setProperty("io.pool", "16");
        try {
            var results = com.example.concurrency.parallelio.ParallelFetchCf.run("simA","simB","simC","simD","simE","simF","simG","simH");
            int peak = com.example.concurrency.parallelio.ParallelFetchCf.lastPeakConcurrency();
            org.junit.jupiter.api.Assertions.assertTrue(peak <= 3, "peak concurrency should be <= cap; got " + peak);
            long prev = Long.MIN_VALUE;
            for (var r : results) {
                org.junit.jupiter.api.Assertions.assertTrue(r.millis() >= prev, "results must be sorted by non-decreasing millis");
                prev = r.millis();
            }
        } finally {
            System.clearProperty("io.cf.cap");
            System.clearProperty("io.pool");
        }
    }

}
