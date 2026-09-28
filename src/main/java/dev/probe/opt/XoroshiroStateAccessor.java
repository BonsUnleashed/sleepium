package dev.probe.opt;

import dev.probe.optsupport.ReusableRandom;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenAlloc (randoms): set a generator's state as its constructor does. */
@Mixin(Xoroshiro128PlusPlus.class)
public interface XoroshiroStateAccessor extends ReusableRandom.State {
    @Override
    @Accessor("seedLo")
    void probe$setLo(long v);

    @Override
    @Accessor("seedHi")
    void probe$setHi(long v);
}
