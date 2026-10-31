package dev.sleep.opt;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/frame-garbage: Vec3.fromRGB24 (fog and sky colours: 216 fetches per sampler call, every frame, from a handful of
 * biome colours) answered from a small direct-mapped cache. The function is pure and Vec3 immutable, so a cached
 * instance is the same value. Entries are immutable holders published by one reference write: a race only misses.
 * An overwrite, not a cancellable inject: that one allocated a CallbackInfoReturnable per call (23 % of the render
 * thread's garbage).
 */
@Mixin(Vec3.class)
public abstract class Vec3FromRgbMixin {
    @Unique private static final dev.sleep.optsupport.MixinHelpers.RgbEntry[] sleep$rgb = new dev.sleep.optsupport.MixinHelpers.RgbEntry[256];

    /**
     * @author probe
     * @reason the same value from a cache (see the class comment)
     */
    @Overwrite
    public static Vec3 fromRGB24(int packed) {
        int i = (packed * 0x9E3779B1) >>> 24;
        var e = sleep$rgb[i];
        if (e != null && e.rgb() == packed) return e.v();
        double d0 = (double) (packed >> 16 & 0xFF) / 255.0;
        double d1 = (double) (packed >> 8 & 0xFF) / 255.0;
        double d2 = (double) (packed & 0xFF) / 255.0;
        Vec3 v = new Vec3(d0, d1, d2);
        sleep$rgb[i] = new dev.sleep.optsupport.MixinHelpers.RgbEntry(packed, v);
        return v;
    }
}
