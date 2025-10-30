package com.example.concurrency.deadlock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the deadlock demo and fixes.
 *
 * Note: The demo intentionally creates a real deadlock using daemon threads so the JVM won't hang,
 * but those deadlocked daemon threads can persist for the remainder of the test JVM.
 * Therefore, when verifying the fix variants, we baseline the current deadlock count and assert
 * that running the fixes does NOT introduce any additional deadlocks.
 */
public class DeadlockDemoSmokeTest {

    @Test
    @DisplayName("Deadlock demo runs without hanging the test JVM")
    void runs() throws Exception {
        DeadlockDemo.main(new String[0]);
        assertTrue(true);
    }

    @Test
    @DisplayName("DeadlockDemo triggers detector within time window")
    void detectorSeesDeadlock() {
        Thread invoker = new Thread(() -> {
            try {
                DeadlockDemo.main(new String[0]);
            } catch (Exception ignored) {
            }
        }, "deadlock-invoker");

        invoker.setDaemon(true);
        invoker.start();

        try {
            assertTrue(
                DeadlockDetector.awaitDeadlock(2000, 50),
                "detector should observe a deadlock within 2s"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Best practice
            throw new AssertionError("Test was interrupted unexpectedly", e);
        }

        DeadlockDetector.printIfAny(System.out);
    }

    @Test
    @DisplayName("Fix variants introduce no NEW deadlocks")
    void fixVariantsShowNoDeadlocks() {
        // Baseline: there may already be deadlocked daemon threads from the prior demo test.
        int baseline = DeadlockDetector.findDeadlocked().length;

        try {
            DeadlockFix.fixByOrdering();
            DeadlockFix.fixByTryLock();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Fix variant was interrupted unexpectedly", e);
        }

        // Allow a brief moment for any would-be deadlocks to manifest
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Sleep interrupted unexpectedly", e);
        }

        int after = DeadlockDetector.findDeadlocked().length;

        // Assert that running the fixes did not increase the number of deadlocked threads.
        // (We don't assert absolute zero because the demo test may have left daemon deadlocked threads alive.)
        assertEquals(baseline, after, "fix variants must not introduce new deadlocks");
    }
}
