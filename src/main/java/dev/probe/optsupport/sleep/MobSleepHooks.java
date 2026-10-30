package dev.probe.optsupport.sleep;

/** Public entry points of the mob sleep mixins (MobSleepRandomMixin, MobSleepGoalsMixin). */
public final class MobSleepHooks {
    private MobSleepHooks() {}

    public static boolean recording(Object random) {
        return MobSleep.REC == random;
    }

    public static void draw(int kind, int bound, long valueBits) {
        MobSleep.draw(kind, bound, valueBits);
    }

    /** A goal selector's modification counter (goals added or removed: a sleeping mob's contexts are stale). */
    public interface GoalEpoch {
        int probe$goalEpoch();
    }
}
