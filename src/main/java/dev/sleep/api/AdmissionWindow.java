package dev.sleep.api;

/**
 * Feedback for owner-thread work that may stretch a tick — the rule probe's FULL ring uses. Two limits:
 * <ul>
 *   <li>time: an item is admitted only if, at its measured average cost (EWMA of {@link #cost}), it ends before the
 *       deadline with a margin of one more item;</li>
 *   <li>window: at most {@code window} items per tick; after each tick the window grows by one if the tick took less
 *       than the target and shrinks to 70 % if it took longer (AIMD, as TCP congestion control).</li>
 * </ul>
 * Single-threaded: use it from the owner thread only.
 *
 * <pre>{@code
 * AdmissionWindow w = new AdmissionWindow(25_000_000L, 16);   // 25 ms tick target
 * // each tick: w.onTick(lastTickNanos);
 * while (queue has work && w.admit(System.nanoTime(), nextTickDeadline)) {
 *     long t = System.nanoTime(); runOne(); w.cost(System.nanoTime() - t);
 * }
 * }</pre>
 */
public final class AdmissionWindow {
    private final long targetNanos;
    private final int maxWindow;
    private double window;
    private int allowance;
    private double costNanos = 50_000;

    /** @param targetTickNanos tick length the window aims below; @param initialWindow items per tick to start with */
    public AdmissionWindow(long targetTickNanos, int initialWindow) {
        this(targetTickNanos, initialWindow, 1024);
    }

    public AdmissionWindow(long targetTickNanos, int initialWindow, int maxWindow) {
        this.targetNanos = targetTickNanos;
        this.maxWindow = maxWindow;
        this.window = Math.max(1, initialWindow);
        this.allowance = (int) window;
    }

    /** after each tick: adapt the window to how long the tick took, and refill it */
    public void onTick(long tickNanos) {
        window = tickNanos < targetNanos ? Math.min(window + 1, maxWindow) : Math.max(1, window * 0.7);
        allowance = (int) window;
    }

    /** may one more item run now? Counts it against the window when the answer is yes. */
    public boolean admit(long nowNanos, long deadlineNanos) {
        if (allowance <= 0 || nowNanos + 2 * costNanos > deadlineNanos) return false;
        allowance--;
        return true;
    }

    /** the time one item took */
    public void cost(long nanos) {
        costNanos += (nanos - costNanos) * 0.1;
    }

    /** items per tick right now */
    public int window() {
        return (int) window;
    }

    /** average cost of one item, ns */
    public double averageCostNanos() {
        return costNanos;
    }
}
