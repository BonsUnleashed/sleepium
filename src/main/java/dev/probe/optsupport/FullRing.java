package dev.probe.optsupport;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * WorldgenReactor (completion ring with feedback): the FULL step's main-thread part (new LevelChunk, post-load, block
 * entities, tick containers, ChunkEvent.Load) arrives here instead of the main-thread mailbox, bucketed by ticket
 * level (lower first, as ChunkTaskPriorityQueueSorter orders). Vanilla runs every queued FULL task at once in the idle
 * phase; a generation wave finishing together gave ~200 chunks in one idle phase and their first ticks in one tick.
 *
 * <p>Feedback, as a congestion window (AIMD):
 * <ul>
 *   <li>time: in the idle phase a FULL task runs only if, at its measured average cost, it ends before the next tick's
 *       deadline with a margin — the idle time is used, never overrun;</li>
 *   <li>window: at most {@code cwnd} chunks enter per tick; after each tick, cwnd grows by 1 if the tick took less than
 *       the target ({@code lab.worldgen.tickTargetMs}, 25 ms) and shrinks ×0.7 if it took longer — the new chunks'
 *       first ticks (leaves, fluids) are what makes a tick long, so admission follows what the ticks can absorb;</li>
 *   <li>without ticks (spawn preparation) the window refills every 50 ms;</li>
 *   <li>when the main thread blocks on a chunk future (managedBlock) there is no limit: it must make progress.</li>
 * </ul>
 * Only the moment a chunk becomes FULL moves; the work is vanilla's.
 */
public final class FullRing {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.WorldgenReactor");
    private static final long TARGET_NANOS = Long.getLong("lab.worldgen.tickTargetMs", 25L) * 1_000_000L;
    private static final int LEVELS = 64;
    @SuppressWarnings("unchecked")
    private static final ConcurrentLinkedQueue<Runnable>[] QUEUES = new ConcurrentLinkedQueue[LEVELS];
    private static final AtomicInteger SIZE = new AtomicInteger();
    public static final LongAdder RAN = new LongAdder(), HELD_BY_WINDOW = new LongAdder(), HELD_BY_TIME = new LongAdder();

    /** main thread only */
    public static boolean idle;
    /** main thread: the deadline of the next tick while idle */
    public static long deadlineNanos;
    private static double cwnd = 16;
    private static int allowance = 16;
    private static long lastRefill;
    /** EWMA of one FULL task's main-thread time */
    private static double costNanos = 60_000;
    private static double cwndSum;
    private static long cwndSamples;

    static {
        for (int i = 0; i < LEVELS; i++) QUEUES[i] = new ConcurrentLinkedQueue<>();
    }

    private FullRing() {}

    /** from any thread */
    public static void offer(int ticketLevel, Runnable fullTask) {
        QUEUES[Math.max(0, Math.min(LEVELS - 1, ticketLevel))].add(fullTask);
        SIZE.incrementAndGet();
        MainReactor.signal();
    }

    /** main thread, after each server tick: the congestion window follows the tick's cost */
    public static void onTick(long tickNanos) {
        if (tickNanos < TARGET_NANOS) cwnd = Math.min(cwnd + 1, 1024);
        else cwnd = Math.max(1, cwnd * 0.7);
        allowance = (int) cwnd;
        lastRefill = System.nanoTime();
        cwndSum += cwnd;
        cwndSamples++;
    }

    private static void refillWithoutTicks() {
        long now = System.nanoTime();
        if (now - lastRefill >= 50_000_000L) {
            allowance = (int) cwnd;
            lastRefill = now;
        }
    }

    /** main thread, idle: may a FULL task run now? */
    private static boolean mayRun() {
        refillWithoutTicks();
        if (allowance <= 0) {
            HELD_BY_WINDOW.increment();
            return false;
        }
        if (System.nanoTime() + 2 * costNanos > deadlineNanos) {
            HELD_BY_TIME.increment();
            return false;
        }
        return true;
    }

    /** main thread, idle: would runOne() run something now? (so the reactor does not sleep past work it may do) */
    public static boolean pending() {
        return SIZE.get() > 0 && mayRun();
    }

    /** main thread: run one FULL task if allowed; true if one ran */
    public static boolean runOne() {
        if (SIZE.get() == 0) return false;
        if (idle && !mayRun()) return false;
        for (int i = 0; i < LEVELS; i++) {
            Runnable r = QUEUES[i].poll();
            if (r != null) {
                SIZE.decrementAndGet();
                if (idle) allowance--;
                RAN.increment();
                MainReactor.found("FULL ring");
                long t = System.nanoTime();
                r.run();
                costNanos += ((System.nanoTime() - t) - costNanos) * 0.1;
                return true;
            }
        }
        return false;
    }

    public static String report() {
        String s = String.format(java.util.Locale.ROOT,
                "full ring: %d FULL steps run, window %.1f chunks/tick on average (now %.0f), FULL cost %.0f us, held by the window %d / by the deadline %d times, %d waiting",
                RAN.sumThenReset(), cwndSamples == 0 ? cwnd : cwndSum / cwndSamples, cwnd, costNanos / 1e3,
                HELD_BY_WINDOW.sumThenReset(), HELD_BY_TIME.sumThenReset(), SIZE.get());
        cwndSum = 0;
        cwndSamples = 0;
        return s;
    }
}
