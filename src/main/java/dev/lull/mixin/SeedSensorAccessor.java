package dev.lull.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exactness tests: Sensor's static random (a new sensor's first scan tick), reseeded by TestSeeds.reseed. */
@Mixin(Sensor.class)
public interface SeedSensorAccessor {
    @Accessor("RANDOM")
    static RandomSource sleep$random() {
        throw new AssertionError();
    }
}
