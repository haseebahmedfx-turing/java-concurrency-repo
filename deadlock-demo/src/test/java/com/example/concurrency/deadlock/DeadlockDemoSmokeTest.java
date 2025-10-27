package com.example.concurrency.deadlock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test that simply runs the demo main for a moment.
 * We assert that the method returns without hanging (threads are daemon).
 */
public class DeadlockDemoSmokeTest {

    @Test
    @DisplayName("Deadlock demo runs without hanging the test JVM")
    void runs() throws Exception {
        DeadlockDemo.main(new String[0]);
        assertTrue(true);
    }
}
