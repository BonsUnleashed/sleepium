package dev.lull.opt;

import dev.lull.optsupport.sleep.MobSleepHooks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mob sleep (lab.opt.EntitySleep + lab.sleep.components + lab.sleep.mobRuntime): the draws of BitRandomSource,
 * overridden in LegacyRandomSource with the same code (copied from 1.21.1's bytecode), reporting each draw of the random
 * of the entity being observed - its kind, bound and value - for the replay and the look-ahead.
 */
@Mixin(LegacyRandomSource.class)
// 1.20.1: implements BitRandomSource so that the jar's reobfuscation renames these overrides to their SRG names
// (unannotated merged methods are not in the refmap: under Mojang's names they would never be called in production).
// 1.21: Mojang names at run time.
public abstract class MobSleepRandomMixin implements net.minecraft.world.level.levelgen.BitRandomSource {
    @Shadow
    public abstract int next(int bits);

    public int nextInt() {
        int v = next(32);
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(0, 0, v);
        return v;
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        int v;
        if ((bound & bound - 1) == 0) {
            v = (int) ((long) bound * (long) next(31) >> 31);
        } else {
            int i, j;
            do {
                i = next(31);
                j = i % bound;
            } while (i - j + (bound - 1) < 0);
            v = j;
        }
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(1, bound, v);
        return v;
    }

    public long nextLong() {
        int i = next(32);
        int j = next(32);
        long v = ((long) i << 32) + (long) j;
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(2, 0, v);
        return v;
    }

    public boolean nextBoolean() {
        boolean v = next(1) != 0;
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(3, 0, v ? 1 : 0);
        return v;
    }

    public float nextFloat() {
        float v = (float) next(24) * 5.9604645E-8F;
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(4, 0, Float.floatToRawIntBits(v));
        return v;
    }

    public double nextDouble() {
        int i = next(26);
        int j = next(27);
        long k = ((long) i << 27) + (long) j;
        double v = (double) k * 1.1102230246251565E-16;
        if (MobSleepHooks.recording(this)) MobSleepHooks.draw(5, 0, Double.doubleToRawLongBits(v));
        return v;
    }
}
