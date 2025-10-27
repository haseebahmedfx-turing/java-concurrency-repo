package com.example.concurrency.deadlock;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ensures the fix methods complete quickly (i.e., no deadlock). */
public class DeadlockFixTest {

    @Test
    @DisplayName("Consistent ordering completes under 2s")
    void orderingCompletes() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> DeadlockFix.fixByOrdering());
    }

    @Test
    @DisplayName("tryLock backoff completes under 2s")
    void tryLockCompletes() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> DeadlockFix.fixByTryLock());
    }
}
