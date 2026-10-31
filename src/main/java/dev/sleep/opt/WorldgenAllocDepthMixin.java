package dev.sleep.opt;

import dev.sleep.optsupport.ReusableRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** WorldgenAlloc (randoms): the surface depth's per-column random, reused (see ReusableRandom). */
@Mixin(SurfaceSystem.class)
public abstract class WorldgenAllocDepthMixin {
    @Redirect(method = "getSurfaceDepth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private RandomSource probe$at(PositionalRandomFactory f, int x, int y, int z) {
        return ReusableRandom.at(f, x, y, z, ReusableRandom.DEPTH);
    }
}
