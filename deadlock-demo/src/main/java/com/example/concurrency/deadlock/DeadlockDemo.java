package com.example.concurrency.deadlock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Intentionally creates a deadlock using opposite lock ordering.
 * Uses daemon threads to avoid hanging the JVM on exit.
 */
public class DeadlockDemo {
    public static void main(String[] args) throws InterruptedException {
        Lock a = new ReentrantLock();
        Lock b = new ReentrantLock();
        Thread t1 = new Thread(() -> lockInOrder(a, b), "T1");
        Thread t2 = new Thread(() -> lockInOrder(b, a), "T2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        Thread.sleep(1200);
        System.out.printf("T1 alive=%s, T2 alive=%s%n", t1.isAlive(), t2.isAlive());
    }

    private static void lockInOrder(Lock first, Lock second) {
        first.lock();
        try {
            sleep(100);
            second.lock();
            try {
                sleep(100);
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
