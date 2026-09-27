package dev.probe.opt;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/frame-garbage: Vec3.fromRGB24 (fog and sky colours: 216 fetches per sampler call, every frame, from a handful of
 * biome colours) answered from a small direct-mapped cache. The function is pure and Vec3 immutable, so a cached
 * instance is the same value. Entries are immutable holders published by one reference write: a race only misses.
 */
@Mixin(Vec3.class)
public abstract class Vec3FromRgbMixin {
    @Unique
    private record Entry(int rgb, Vec3 v) {}

    @Unique private static final Entry[] probe$rgb = new Entry[256];

    @Inject(method = "fromRGB24", at = @At("HEAD"), cancellable = true)
    private static void probe$cached(int packed, CallbackInfoReturnable<Vec3> cir) {
        int i = (packed * 0x9E3779B1) >>> 24;
        Entry e = probe$rgb[i];
        if (e != null && e.rgb() == packed) {
            cir.setReturnValue(e.v());
            return;
        }
        Vec3 v = new Vec3((double) (packed >> 16 & 0xFF) / 255.0, (double) (packed >> 8 & 0xFF) / 255.0, (double) (packed & 0xFF) / 255.0);
        probe$rgb[i] = new Entry(packed, v);
        cir.setReturnValue(v);
    }
}
