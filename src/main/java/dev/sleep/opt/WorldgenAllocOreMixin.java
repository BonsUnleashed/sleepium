package dev.sleep.opt;

import dev.sleep.optsupport.ReusableRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** WorldgenAlloc (randoms): the ore vein filler's per-block random, reused (see ReusableRandom). */
@Mixin(OreVeinifier.class)
public abstract class WorldgenAllocOreMixin {
    @Redirect(method = "lambda$create$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource probe$at(PositionalRandomFactory f, int x, int y, int z) {
        return ReusableRandom.at(f, x, y, z, ReusableRandom.ORE);
    }
}
