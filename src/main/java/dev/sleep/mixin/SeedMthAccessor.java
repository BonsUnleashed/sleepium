package dev.sleep.mixin;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exactness tests: Mth's static random, reseeded by TestSeeds.reseed. */
@Mixin(Mth.class)
public interface SeedMthAccessor {
    @Accessor("RANDOM")
    static RandomSource sleep$random() {
        throw new AssertionError();
    }
}
