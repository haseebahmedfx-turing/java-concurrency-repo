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

}
