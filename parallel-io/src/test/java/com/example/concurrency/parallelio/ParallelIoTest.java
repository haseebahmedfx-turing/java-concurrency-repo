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
}
