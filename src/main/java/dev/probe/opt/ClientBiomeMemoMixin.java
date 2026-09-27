package dev.probe.opt;

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
public abstract class ClientBiomeMemoMixin implements LevelReader, dev.probe.optsupport.BiomeMemo {
    @Unique private final Long2ObjectOpenHashMap<Holder<Biome>> probe$biomes = new Long2ObjectOpenHashMap<>();
    @Unique private static long probe$hits, probe$misses, probe$clears;

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (!RenderSystem.isOnRenderThread()) return LevelReader.super.getNoiseBiome(x, y, z);
        long k = BlockPos.asLong(x, y, z);
        Holder<Biome> h = probe$biomes.get(k);
        if (h != null) {
            probe$hits++;
            return h;
        }
        h = LevelReader.super.getNoiseBiome(x, y, z);
        if (probe$biomes.size() >= 65536) probe$biomes.clear();
        probe$biomes.put(k, h);
        probe$misses++;
        return h;
    }

    @Override
    public void probe$biomesChanged() {
        if (!probe$biomes.isEmpty()) {
            probe$biomes.clear();
            probe$clears++;
        }
    }

    @Override
    public String probe$biomeReport() {
        return "biome memo: hits " + probe$hits + ", misses " + probe$misses + ", clears " + probe$clears;
    }
}
