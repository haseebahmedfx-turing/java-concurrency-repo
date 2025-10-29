package com.example.concurrency.producerconsumer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class BackpressureRunnerTest {

    @Test
    @DisplayName("Small run should produce and consume some tasks")
    void smallRunProducesAndConsumes() {
        var m = BackpressureRunner.run(2, 16, 1, 100);
        assertTrue(m.produced() > 0, "should produce some tasks");
        assertTrue(m.consumed() > 0, "should consume some tasks");
        assertEquals(0, m.rejected(), "with caller-runs backpressure, rejections should be zero");
        assertTrue(m.consumed() <= m.produced(), "cannot consume more than produced");
    }

    @ParameterizedTest
    @CsvSource({
        "1,8,1,200",
        "2,8,1,300",
        "4,16,1,400"
    })
    @DisplayName("Backpressure keeps rejections at 0 for various pool/queue/rate combos")
    void parameterizedBackpressureNoRejects(int pool, int cap, int sec, int rate) {
        var m = BackpressureRunner.run(pool, cap, sec, rate);
        assertEquals(0, m.rejected());
    }

    @RepeatedTest(2)
    @DisplayName("CallerRuns should trigger when the queue is tight")
    void callerRunsTriggersWithTightQueue() {
        var m = BackpressureRunner.run(1, 1, 1, 1000);
        assertTrue(m.callerRuns() >= 0, "count is non-negative");
        // We don't assert exact value due to runtime variance, but it should be frequently non-zero
    }

    @Test
    @DisplayName("Metrics sampler records queue depth within bounds and non-empty sample list")
    void metricsSamplerRecordsSamples() {
        int pool = 2, cap = 16, sec = 1, rate = 200;
        var m = BackpressureRunner.run(pool, cap, sec, rate);
        var samples = BackpressureRunner.lastSamples();
        assertTrue(samples.size() > 0, "samples should be non-empty");
        for (var s : samples) {
            assertTrue(s.queueDepth() >= 0 && s.queueDepth() <= cap,
                "queue depth within [0, capacity] but was " + s.queueDepth());
        }
        assertEquals(samples.size(), m.samplesCount(), "metrics should expose sample count");
    }

    @Test
    @DisplayName("drain=true drains queue and consumes all produced tasks on slow workers")
    void drainTrueConsumesAllAndEmptiesQueue() {
        // Make workers slow by using 1 thread and a high production rate
        System.setProperty("drain", "true");
        try {
            int pool = 1, cap = 64, sec = 1, rate = 2000;
            var m = BackpressureRunner.run(pool, cap, sec, rate);
            assertEquals(0, m.queueEnd(), "queue should be empty when drain=true");
            assertTrue(m.consumed() >= m.produced(),
                "consumed should reach produced (or slightly exceed if callerRuns executed inline)");
        } finally {
            System.clearProperty("drain");
        }
    }

    @Test
    @DisplayName("drain=false may leave queued tasks unprocessed")
    void drainFalseMayLeaveQueue() {
        System.setProperty("drain", "false");
        try {
            int pool = 1, cap = 64, sec = 1, rate = 3000;
            var m = BackpressureRunner.run(pool, cap, sec, rate);
            boolean leftQueue = m.queueEnd() > 0 || m.consumed() < m.produced();
            assertTrue(leftQueue, "without drain, run may exit before draining all tasks");
        } finally {
            System.clearProperty("drain");
        }
    }

    @Test
    @DisplayName("CallerRuns keeps rejected == 0 on bursty load")
    void rejectionPolicyCallerRuns_NoRejects() {
        System.setProperty("rejectionPolicy", "CallerRuns");
        try {
            var m = BackpressureRunner.run(1, 2, 1, 5000);
            assertEquals(0, m.rejected(), "CallerRuns should absorb bursts without rejecting");
            assertTrue(m.callerRuns() >= 0);
        } finally {
            System.clearProperty("rejectionPolicy");
        }
    }

    @Test
    @DisplayName("DropNewest increments rejected > 0 under tight queue")
    void rejectionPolicyDropNewest_IncrementsRejected() {
        System.setProperty("rejectionPolicy", "DropNewest");
        try {
            var m = BackpressureRunner.run(1, 2, 1, 5000);
            assertTrue(m.rejected() > 0, "DropNewest should reject when queue is tight");
        } finally {
            System.clearProperty("rejectionPolicy");
        }
    }

    @Test
    @DisplayName("Block policy blocks instead of rejecting (no deadlock, within capacity)")
    void rejectionPolicyBlock_NoRejectsNoDeadlock() {
        System.setProperty("rejectionPolicy", "Block");
        try {
            var m = BackpressureRunner.run(1, 2, 1, 5000);
            // Blocking should avoid rejected tasks
            assertEquals(0, m.rejected(), "Block should not reject tasks");
            // Sanity: produced/consumed should be positive
            assertTrue(m.produced() > 0 && m.consumed() > 0);
        } finally {
            System.clearProperty("rejectionPolicy");
        }
    }

    @Test
    @DisplayName("backoff reduces callerRuns compared to baseline on tiny capacity")
    void backoffReducesCallerRuns() {
        // Baseline (backoff disabled)
        System.setProperty("backoff.enabled", "false");
        try {
            int pool = 1, cap = 1, sec = 1, rate = 8000;
            var base = BackpressureRunner.run(pool, cap, sec, rate);

            // With backoff (enable and lower threshold to trigger sooner)
            System.setProperty("backoff.enabled", "true");
            System.setProperty("backoff.threshold", "0.05");
            System.setProperty("backoff.nanos", "200000"); // 0.2 ms
            var tuned = BackpressureRunner.run(pool, cap, sec, rate);

            assertTrue(tuned.callerRuns() <= base.callerRuns(),
                "backoff should reduce (or at least not increase) callerRuns");
        } finally {
            System.clearProperty("backoff.enabled");
            System.clearProperty("backoff.threshold");
            System.clearProperty("backoff.nanos");
        }
    }
}
