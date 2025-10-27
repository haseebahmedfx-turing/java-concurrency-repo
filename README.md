# Java Concurrency Repo (Producer/Consumer • Deadlocks • CF vs Loom)

A compact, **Java 21** + **Maven** codebase meant for learning the following:

- **producer-consumer** — Bounded thread pool with **backpressure** and metrics.
- **deadlock-demo** — Minimal **deadlock** reproduction and two **fix** strategies.
- **parallel-io** — Fan‑out requests in parallel via **CompletableFuture** and **Virtual Threads (Loom)**.


---

## Quick Start

```bash
# Build & run tests
mvn -ntp verify

# Run a module's demo (examples)
mvn -q -pl producer-consumer -am exec:java   -Dexec.mainClass=com.example.concurrency.producerconsumer.ProducerConsumerDemo
mvn -q -pl deadlock-demo     -am exec:java   -Dexec.mainClass=com.example.concurrency.deadlock.DeadlockDemo
mvn -q -pl deadlock-demo     -am exec:java   -Dexec.mainClass=com.example.concurrency.deadlock.DeadlockFix
mvn -q -pl parallel-io       -am exec:java   -Dexec.mainClass=com.example.concurrency.parallelio.ParallelFetchCf   -Dexec.args="simA simB"
mvn -q -pl parallel-io       -am exec:java   -Dexec.mainClass=com.example.concurrency.parallelio.ParallelFetchLoom -Dexec.args="simX simY"
```

### Requirements
- JDK **21** (Temurin recommended)
- Maven **3.9+**  
(CI uses GitHub Actions `setup-java@v4` with JDK 21 and runs `mvn -ntp verify`.)

---

## Modules

### 1) `producer-consumer`
Demonstrates backpressure using an `ArrayBlockingQueue` and a caller‑runs rejection handler.
Tune via system properties:

- `poolSize` (default **4**) — worker threads
- `queueCapacity` (default **64**) — bounded queue size
- `durationSec` (default **3**) — run length
- `producerRatePerSec` (default **200**) — synthetic produce rate

Smoke tests assert produced/consumed counts and rejection behavior.

### 2) `deadlock-demo`
Reproduces a classic deadlock using opposite lock ordering and provides two fixes:
- **Consistent ordering** of locks
- **`tryLock` with timeout + backoff**

Tests ensure the fix methods complete within a time budget.

### 3) `parallel-io`
Two implementations of parallel fan‑out:
- **`ParallelFetchCf`** — `CompletableFuture` with a fixed pool
- **`ParallelFetchLoom`** — virtual threads (`Thread.ofVirtual()`)

For testing, any non‑HTTP input like `simX` is treated as a **simulated** workload (no network).

---

## Project Layout

```
java-concurrency-1000loc/
├─ pom.xml                    # Parent aggregator
├─ .github/workflows/ci.yml   # JDK 21 + mvn verify
├─ producer-consumer/
│  ├─ src/main/java/com/example/.../BackpressureRunner.java
│  ├─ src/main/java/com/example/.../ProducerConsumerDemo.java
│  └─ src/test/java/com/example/.../BackpressureRunnerTest.java
├─ deadlock-demo/
│  ├─ src/main/java/com/example/.../DeadlockDemo.java
│  ├─ src/main/java/com/example/.../DeadlockFix.java
│  └─ src/test/java/com/example/.../DeadlockFixTest.java
└─ parallel-io/
   ├─ src/main/java/com/example/.../ParallelFetchCf.java
   ├─ src/main/java/com/example/.../ParallelFetchLoom.java
   └─ src/test/java/com/example/.../ParallelIoTest.java
```

---

## Why these examples?

- **Producer/Consumer** shows **backpressure** trade‑offs with bounded queues and rejection policies.
- **Deadlocks** still happen; we highlight **prevention** (ordering) and **recovery** (`tryLock` + backoff).
- **Parallel I/O** is a perfect demo to compare **CF** vs **Loom** semantics and ergonomics.