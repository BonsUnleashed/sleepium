package dev.probe.optsupport.sleep;

/** Added to BlockEntity by SleepProbeHolderMixin: the engine's per-object state lives in the object itself. */
public interface SleeperHolder {
    Object probe$sleeper();

    void probe$setSleeper(Object sleeper);
}
