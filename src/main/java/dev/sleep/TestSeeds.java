package dev.sleep;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * Exactness tests (-Dsleep.seedEntities=true): vanilla's randoms that are seeded from the clock, made reproducible so two
 * runs of a scene draw the same numbers. Entities and levels are seeded where they are made (SeedEntitiesMixin,
 * SeedLevelMixin); {@link #reseed} (/sleep reseed, right before a scene is built) resets the rest: each level's random,
 * thread-safe random and random-tick generator, the static randoms of Sensor (a sensor's first scan tick) and Mth, and
 * the counter that seeds every new ShufflingList (a brain's weighted behaviour order) and Explosion.
 */
public final class TestSeeds {
    private TestSeeds() {}

    public static final boolean ON = Boolean.getBoolean("sleep.seedEntities");
    private static final AtomicLong NEXT = new AtomicLong();

    /** the seed of the next object made with a clock-seeded random */
    public static long next() {
        return NEXT.getAndIncrement() * 0x9E3779B97F4A7C15L + 4242;
    }

    public static void reseed(MinecraftServer server) {
        for (var lv : server.getAllLevels()) {
            long s = lv.dimension().location().toString().hashCode() * 31L + 777;
            lv.random.setSeed(s);
            ((dev.sleep.mixin.SeedLevelAccessor) lv).sleep$threadSafeRandom().setSeed(s + 1);
            ((dev.sleep.mixin.SeedLevelAccessor) lv).sleep$setRandValue((int) (s * 3 + 1));
        }
        RandomSource sensor = dev.sleep.mixin.SeedSensorAccessor.sleep$random();
        sensor.setSeed(91);
        dev.sleep.mixin.SeedMthAccessor.sleep$random().setSeed(92);
        NEXT.set(0);
    }
}
