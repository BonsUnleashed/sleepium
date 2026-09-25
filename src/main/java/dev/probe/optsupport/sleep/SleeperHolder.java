package dev.probe.optsupport.sleep;

/**
 * Added to BlockEntity by SleepProbeHolderMixin: the engine's per-object state lives in the object itself.
 * The two hot fields used by the fast skip are primitives on the block entity (no pointer chase to the Sleeper).
 */
public interface SleeperHolder {
    Object probe$sleeper();

    void probe$setSleeper(Object sleeper);

    /** Game time until which the block entity may be skipped; Long.MIN_VALUE = awake or woken. */
    long probe$wakeAt();

    void probe$setWakeAt(long wakeAt);

    /** Ticks skipped since the clocks were last advanced. */
    long probe$skipped();

    void probe$setSkipped(long skipped);
}
