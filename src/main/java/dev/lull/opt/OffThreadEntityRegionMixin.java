package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/** While a new chunk's starting mobs are spawned, the thread knows its region (dev.lull.optsupport.OffThreadEntity). */
@Mixin(NaturalSpawner.class)
public abstract class OffThreadEntityRegionMixin {
    @WrapMethod(method = "spawnMobsForChunkGeneration")
    private static void sleep$region(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos pos, RandomSource random, Operation<Void> original) {
        dev.lull.optsupport.OffThreadEntity.enter(level);
        try {
            original.call(level, biome, pos, random);
        } finally {
            dev.lull.optsupport.OffThreadEntity.exit();
        }
    }
}
