package dev.sleep.optsupport.sleep;

/** Public entry points of the mob sleep mixins (MobSleepRandomMixin, MobSleepGoalsMixin). */
public final class MobSleepHooks {
    /** the draw call site in progress (DrawSiteIds), set around the call by the write barrier's transform; -1 none */
    public static int site = -1;

    private MobSleepHooks() {}

    public static boolean recording(Object random) {
        return MobSleep.REC == random;
    }

    public static void draw(int kind, int bound, long valueBits) {
        MobSleep.draw(kind, bound, valueBits);
    }

    /** A goal selector's modification counter (goals added or removed: a sleeping mob's contexts are stale). */
    public interface GoalEpoch {
        int sleep$goalEpoch();
    }
}
