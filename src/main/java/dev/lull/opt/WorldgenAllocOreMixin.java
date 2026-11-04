package dev.lull.opt;

import dev.lull.optsupport.ReusableRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** WorldgenAlloc (randoms): the ore vein filler's per-block random, reused (see ReusableRandom). */
@Mixin(OreVeinifier.class)
public abstract class WorldgenAllocOreMixin {
    // the descriptor lets the refmap map the lambda (SRG-named on Forge 1.20.1)
    @Redirect(method = "lambda$create$0(Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource sleep$at(PositionalRandomFactory f, int x, int y, int z) {
        return ReusableRandom.at(f, x, y, z, ReusableRandom.ORE);
    }
}
