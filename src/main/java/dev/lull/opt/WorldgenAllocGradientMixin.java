package dev.lull.opt;

import dev.lull.optsupport.ReusableRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** WorldgenAlloc (randoms): the vertical gradient condition's per-block random, reused (see ReusableRandom). */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$VerticalGradientConditionSource$1VerticalGradientCondition")
public abstract class WorldgenAllocGradientMixin {
    @Redirect(method = "compute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private RandomSource sleep$at(PositionalRandomFactory f, int x, int y, int z) {
        return ReusableRandom.at(f, x, y, z, ReusableRandom.GRADIENT);
    }
}
