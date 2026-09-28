package dev.probe.opt;

import dev.probe.optsupport.ReusableRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenAlloc (randoms): the positional factory's seed halves. */
@Mixin(XoroshiroRandomSource.XoroshiroPositionalRandomFactory.class)
public interface XoroshiroFactoryAccessor extends ReusableRandom.Factory {
    @Override
    @Accessor("seedLo")
    long probe$seedLo();

    @Override
    @Accessor("seedHi")
    long probe$seedHi();
}
