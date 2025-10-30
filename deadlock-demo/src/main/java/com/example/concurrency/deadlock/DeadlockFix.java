package com.example.concurrency.deadlock;

import java.lang.management.ThreadInfo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Two minimal fixes: consistent ordering and timed tryLock with backoff. */
public class DeadlockFix {
    private static void printNoDeadlocksIfNone() {
        if (DeadlockDetector.findDeadlocked().length == 0) {
            System.out.println("[DeadlockDetector] no deadlocks detected in fix variant");
        }
    }


    static class Ordered {
        final ReentrantLock a = new ReentrantLock();
        final ReentrantLock b = new ReentrantLock();
    }

    public static void main(String[] args) throws InterruptedException {
        fixByOrdering();
        fixByTryLock();
    }

    /** Ensure both threads acquire locks in the same order. */
    public static void fixByOrdering() throws InterruptedException {
        var o = new Ordered();
        Runnable r = () -> {
            o.a.lock();
            try {
                sleep(20);
                o.b.lock();
                try {
                    sleep(20);
                } finally {
                    o.b.unlock();
                }
            } finally {
                o.a.unlock();
            }
        };
        Thread t1 = new Thread(r, "order-1");
        Thread t2 = new Thread(r, "order-2");
        t1.start(); t2.start();
        t1.join(); t2.join();
    }

    /** Use timed tryLock to avoid circular wait. */
    public static void fixByTryLock() throws InterruptedException {
        var a = new ReentrantLock();
        var b = new ReentrantLock();
        Thread t1 = new Thread(() -> cautious(a, b), "try-1");
        Thread t2 = new Thread(() -> cautious(b, a), "try-2");
        t1.start(); t2.start();
        t1.join(); t2.join();
    }

    private static void cautious(ReentrantLock first, ReentrantLock second) {
        boolean done = false;
        while (!done) {
            try {
                if (first.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        if (second.tryLock(100, TimeUnit.MILLISECONDS)) {
                            try {
                                sleep(15);
                                done = true;
                            } finally {
                                second.unlock();
                            }
                        }
                    } finally {
                        first.unlock();
                    }
                }
                if (!done) sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
