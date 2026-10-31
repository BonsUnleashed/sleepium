package dev.sleep.optsupport.sleep;

/**
 * Added to BlockEntity by SleepProbeHolderMixin: the engine's per-object state lives in the object itself.
 * The two hot fields used by the fast skip are primitives on the block entity (no pointer chase to the Sleeper).
 */
public interface SleeperHolder {
    Object sleep$sleeper();

    void sleep$setSleeper(Object sleeper);

    /** Game time until which the block entity may be skipped; Long.MIN_VALUE = awake or woken. */
    long sleep$wakeAt();

    void sleep$setWakeAt(long wakeAt);

    /** Ticks skipped since the clocks were last advanced. */
    long sleep$skipped();

    void sleep$setSkipped(long skipped);

    /** Ticks observed before a Sleeper is created (short-lived block entities never get one). */
    int sleep$age();

    void sleep$setAge(int age);
}
