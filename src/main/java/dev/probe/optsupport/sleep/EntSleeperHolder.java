package dev.probe.optsupport.sleep;

/** Added to Entity by EntSleepHolderMixin: the entity's sleep record (null until its first tick through the engine). */
public interface EntSleeperHolder {
    Object probe$es();

    void probe$setEs(Object s);
}
