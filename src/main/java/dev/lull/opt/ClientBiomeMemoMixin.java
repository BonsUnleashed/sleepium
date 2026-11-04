package dev.lull.opt;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/client-biome-memo (client): the render thread's noise-biome lookups (fog colour: 216 per frame, sky colour, the
 * ambient particles of animateTick: ~1300 per tick) answered from a per-level memo of quart position -> biome holder.
 * The answer is what LevelReader.getNoiseBiome gives for the chunk data at hand; ClientChunkCacheBiomeMixin clears the
 * memo whenever that data can change (chunk loaded, updated, dropped, biomes replaced, view centre or radius moved).
 * Other threads (section compiles ask for tints) go the vanilla way.
 */
@Mixin(ClientLevel.class)
public abstract class ClientBiomeMemoMixin implements LevelReader, dev.lull.optsupport.BiomeMemo {
    @Unique private final Long2ObjectOpenHashMap<Holder<Biome>> sleep$biomes = new Long2ObjectOpenHashMap<>();
    @Unique private static long sleep$hits, sleep$misses, sleep$clears;

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (!RenderSystem.isOnRenderThread()) return sleep$vanillaBiome(x, y, z);
        long k = BlockPos.asLong(x, y, z);
        Holder<Biome> h = sleep$biomes.get(k);
        if (h != null) {
            sleep$hits++;
            return h;
        }
        h = sleep$vanillaBiome(x, y, z);
        if (sleep$biomes.size() >= 65536) sleep$biomes.clear();
        sleep$biomes.put(k, h);
        sleep$misses++;
        return h;
    }

    /**
     * LevelReader's default getNoiseBiome, as it reads: Forge 1.20.1's Mixin (0.8.5) turns LevelReader.super.x() in a
     * mixin into a class method reference (IncompatibleClassChangeError at the first call)
     */
    @Unique
    private Holder<Biome> sleep$vanillaBiome(int x, int y, int z) {
        net.minecraft.world.level.chunk.ChunkAccess c = this.getChunk(net.minecraft.core.QuartPos.toSection(x), net.minecraft.core.QuartPos.toSection(z),
                net.minecraft.world.level.chunk.ChunkStatus.BIOMES, false);
        return c != null ? c.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public void sleep$biomesChanged() {
        if (!sleep$biomes.isEmpty()) {
            sleep$biomes.clear();
            sleep$clears++;
        }
    }

    @Override
    public String sleep$biomeReport() {
        return "biome memo: hits " + sleep$hits + ", misses " + sleep$misses + ", clears " + sleep$clears;
    }
}
