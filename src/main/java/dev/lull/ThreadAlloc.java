package dev.lull;

import java.lang.management.ManagementFactory;

/**
 * Bytes allocated by the current thread (HotSpot TLAB accounting).
 * Cheap enough to call twice per tick; returns -1 when the JVM does not support it.
 */
final class ThreadAlloc {
    private static final com.sun.management.ThreadMXBean MX = lookup();

    private ThreadAlloc() {}

    private static com.sun.management.ThreadMXBean lookup() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean mx
                    && mx.isThreadAllocatedMemorySupported()) {
                mx.setThreadAllocatedMemoryEnabled(true);
                return mx;
            }
        } catch (Throwable t) {
            SleepMod.LOGGER.warn("Per-thread allocation accounting unavailable", t);
        }
        return null;
    }

    static boolean supported() {
        return MX != null;
    }

    static long current() {
        return MX != null ? MX.getCurrentThreadAllocatedBytes() : -1L;
    }
}
