package dev.sleep.opt;

import dev.sleep.optsupport.ReusableRandom;
import net.minecraft.world.level.levelgen.MarsagliaPolarGaussian;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenAlloc (randoms): a random source's generator and gaussian state. */
@Mixin(XoroshiroRandomSource.class)
public interface XoroshiroSourceAccessor extends ReusableRandom.Source {
    @Override
    @Accessor("randomNumberGenerator")
    Xoroshiro128PlusPlus sleep$rng();

    @Override
    @Accessor("gaussianSource")
    MarsagliaPolarGaussian sleep$gaussian();
}
