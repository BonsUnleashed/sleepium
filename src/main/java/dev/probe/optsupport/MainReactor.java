package dev.probe.optsupport;

import java.util.Queue;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

/**
 * ServerReactor stage 1: the server thread sleeps until something is to be done. Vanilla's idle loop between ticks
 * (waitUntilNextTick → managedBlock → waitForTasks) parks 100 µs at a time and polls again: up to ~10k wake-ups a
 * second with nothing to do. Here, in that idle phase only, it parks until the next tick deadline (at most
 * {@code lab.reactor.maxParkMs}, 50 ms by default: the deadline itself) unless a
 * producer signals: vanilla tell() (unpark), FullRing offers, light tasks (their scheduling is made event-driven).
 *
 * <p>No lost wake-up: the thread publishes "parked" (volatile), re-checks its queues, then parks; a producer enqueues
 * first and then reads "parked" (volatile) — one of the two always sees the other. Tasks still run in the same order
 * at the same places; only when the thread wakes changes. Blocking waits (managedBlock on a future, e.g. a synchronous
 * getChunk) keep vanilla's 100 µs polling: completing a future does not signal this thread.
 */
public final class MainReactor {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.MainReactor");
    private static final long MAX_PARK_NANOS = Long.getLong("lab.reactor.maxParkMs", 50L) * 1_000_000L;

    /** the server thread (set when it first idles) */
    public static volatile Thread main;
    private static volatile boolean parked;
    /** main thread only: inside waitUntilNextTick's idle wait */
    public static boolean idle;

    public static final LongAdder PARKS = new LongAdder(), EARLY = new LongAdder(), SIGNALS = new LongAdder();
    /** measurement: first event while parked, and the event-to-wake latency */
    private static volatile long eventNanos;
    public static final LongAdder WAKES = new LongAdder(), WAKE_NANOS = new LongAdder();
    private static final long[] WAKE_HIST = new long[6];   // <20us, <100us, <500us, <1ms, <2ms, more

    /** measurement: any thread posted work for the server thread */
    public static void noteEvent() {
        if (parked && eventNanos == 0) eventNanos = System.nanoTime();
    }

    private MainReactor() {}

    /** any thread, after enqueueing work for the server thread */
    public static void signal() {
        if (parked) {
            Thread t = main;
            if (t != null) {
                SIGNALS.increment();
                noteEvent();
                LockSupport.unpark(t);
            }
        }
    }

    /** server thread, idle phase: sleep until the deadline or a signal */
    public static void park(long deadlineNanos, Queue<?>... queues) {
        main = Thread.currentThread();
        // the server's deadlines are in Util.getNanos() time: on the client that is GLFW's timer, not System.nanoTime
        long now = net.minecraft.Util.getNanos();
        long wait = Math.min(deadlineNanos - now, MAX_PARK_NANOS);
        if (wait <= 0) return;
        eventNanos = 0;
        parked = true;
        try {
            for (Queue<?> q : queues) if (q != null && !q.isEmpty()) return;
            if (FullRing.pending()) return;
            PARKS.increment();
            LockSupport.parkNanos("server reactor: waiting for work or the next tick", wait);
            long ev = eventNanos;
            if (ev != 0) {
                long d = System.nanoTime() - ev;
                WAKES.increment();
                WAKE_NANOS.add(d);
                WAKE_HIST[d < 20_000 ? 0 : d < 100_000 ? 1 : d < 500_000 ? 2 : d < 1_000_000 ? 3 : d < 2_000_000 ? 4 : 5]++;
            }
            if (net.minecraft.Util.getNanos() - now < wait - 200_000L) EARLY.increment();
            else if (SCOUT) afterTimeout = true;
        } finally {
            parked = false;
        }
    }

    private static final boolean SCOUT = Boolean.getBoolean("lab.reactor.scout");
    /** main thread: woke by timeout, nothing found yet */
    private static boolean afterTimeout;
    private static final java.util.Map<String, long[]> FOUND = new java.util.TreeMap<>();

    /** measurement: main-thread work found by polling after a timeout wake (its producer did not signal) */
    public static void found(String what) {
        if (!afterTimeout || !idle || Thread.currentThread() != main) return;
        afterTimeout = false;
        FOUND.computeIfAbsent(what, k -> new long[1])[0]++;
    }

    public static String scoutReport() {
        StringBuilder b = new StringBuilder("reactor scout (work found only by polling after a timeout):");
        FOUND.forEach((k, v) -> b.append(' ').append(k).append('=').append(v[0]).append(';'));
        FOUND.clear();
        return b.toString();
    }

    public static String wakeReport() {
        long n = WAKES.sumThenReset();
        String s = String.format(java.util.Locale.ROOT,
                "server reactor wake-up after an event: %d, %.1f us on average; <20us %d, <100us %d, <500us %d, <1ms %d, <2ms %d, more %d",
                n, n == 0 ? 0 : WAKE_NANOS.sumThenReset() / 1e3 / n, WAKE_HIST[0], WAKE_HIST[1], WAKE_HIST[2], WAKE_HIST[3], WAKE_HIST[4], WAKE_HIST[5]);
        java.util.Arrays.fill(WAKE_HIST, 0);
        return s;
    }

    public static String report(double secs) {
        return String.format(java.util.Locale.ROOT, "server reactor: %.0f parks/s, %.0f woken early/s, %.0f signals/s",
                PARKS.sumThenReset() / secs, EARLY.sumThenReset() / secs, SIGNALS.sumThenReset() / secs);
    }
}
