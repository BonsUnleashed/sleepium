package dev.lull.optsupport.sleep;

/** Added to Entity by EntSleepHolderMixin: the entity's sleep record (null until its first tick through the engine). */
public interface EntSleeperHolder {
    Object sleep$es();

    void sleep$setEs(Object s);
}
