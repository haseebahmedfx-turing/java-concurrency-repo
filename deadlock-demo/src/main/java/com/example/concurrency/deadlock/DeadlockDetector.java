package com.example.concurrency.deadlock;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/** Deadlock detector utility using ThreadMXBean.findDeadlockedThreads(). */
public final class DeadlockDetector {
    private DeadlockDetector() {}

    /** Returns the deadlocked thread infos (empty if none). */
    public static ThreadInfo[] findDeadlocked() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();
        if (ids == null || ids.length == 0) return new ThreadInfo[0];
        return bean.getThreadInfo(ids, true, true);
    }

    /** Polls for a deadlock to appear within the timeout. */
    public static boolean awaitDeadlock(long timeoutMillis, long pollMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            if (findDeadlocked().length > 0) return true;
            Thread.sleep(Math.max(1, pollMillis));
        }
        return false;
    }

    /** Prints suspects to the given stream; returns true if any were printed. */
    public static boolean printIfAny(java.io.PrintStream out) {
        ThreadInfo[] infos = findDeadlocked();
        if (infos.length == 0) return false;
        out.println("[DeadlockDetector] DEADLOCK SUSPECTS:");
        for (ThreadInfo ti : infos) {
            out.printf(" - id=%d name=%s state=%s%n", ti.getThreadId(), ti.getThreadName(), ti.getThreadState());
            for (LockInfo li : ti.getLockedSynchronizers()) {
                out.printf("    locked synchronizer: %s%n", li);
            }
            for (MonitorInfo mi : ti.getLockedMonitors()) {
                out.printf("    locked monitor: %s%n", mi);
            }
            if (ti.getLockInfo() != null) {
                out.printf("    waiting on: %s%n", ti.getLockInfo());
            }
        }
        return true;
    }
}
