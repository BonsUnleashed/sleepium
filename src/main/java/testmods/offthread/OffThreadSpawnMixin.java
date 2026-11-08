package testmods.offthread;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test only (-Dsleep.test.offThreadSpawn=true): what Easy Mob Spawn Control does (GitHub #4) - while a new chunk's
 * starting mobs are spawned on a world-generation worker, one extra pig is added straight to the live ServerLevel from
 * that worker. With OffThreadEntity on it lands in the generating chunk; off, it is the vanilla race the mod causes.
 */
@Mixin(NaturalSpawner.class)
public abstract class OffThreadSpawnMixin {
    @Inject(method = "spawnMobsForChunkGeneration", at = @At("TAIL"))
    private static void sleep$extra(ServerLevelAccessor level, Holder<Biome> biome, ChunkPos pos, RandomSource random, CallbackInfo ci) {
        int x = pos.getMiddleBlockX(), z = pos.getMiddleBlockZ();
        Pig pig = EntityType.PIG.create(level.getLevel());
        if (pig == null) return;
        pig.moveTo(x + 0.5, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z + 0.5, 0, 0);
        pig.addTag("sleep_offthread");
        level.getLevel().addFreshEntity(pig);
    }
}
