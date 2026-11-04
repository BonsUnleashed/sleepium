package dev.lull.opt;

import dev.lull.optsupport.ReusableRandom;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenAlloc (randoms): set a generator's state as its constructor does. */
@Mixin(Xoroshiro128PlusPlus.class)
public interface XoroshiroStateAccessor extends ReusableRandom.State {
    @Override
    @Accessor("seedLo")
    void sleep$setLo(long v);

    @Override
    @Accessor("seedHi")
    void sleep$setHi(long v);
}
