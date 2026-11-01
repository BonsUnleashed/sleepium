package dev.sleep.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exactness tests: a level's clock-seeded randoms besides {@code random} (TestSeeds.reseed). */
@Mixin(Level.class)
public interface SeedLevelAccessor {
    @Accessor("threadSafeRandom")
    RandomSource sleep$threadSafeRandom();

    @Accessor("randValue")
    void sleep$setRandValue(int v);
}
