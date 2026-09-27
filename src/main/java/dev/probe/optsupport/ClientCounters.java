package dev.probe.optsupport;

import java.util.Locale;

/** State and counters of the client-side optimisations (render thread unless noted). */
public final class ClientCounters {
    public static long uniformsSent, uniformsSkipped;

    public static long biomeSkips, samplerHits;
    /** frame-hash runs: animated textures stop (FrameHashFreezeMixin) */
    public static volatile boolean freezeTextures;
    /** changes whenever the set of loaded chunks or their biomes may have changed (AmbientChunksMixin) */
    public static long ambientVersion;

    /** Implemented by ClientChunkCache (AmbientChunksMixin). */
    public interface AmbientChunks {
        boolean probe$anyAmbient();
    }

    private ClientCounters() {}

    public static String report() {
        return String.format(Locale.ROOT, "client opts: ambient biome lookups skipped %d, sampler locations from cache %d; collision memo hits %d, misses %d; %s",
                biomeSkips, samplerHits, BlockCollisionMemo.hits, BlockCollisionMemo.misses, EventSites.report())
                + (Boolean.getBoolean("lab.opt.ChunkMultiDrawMixin") ? "; " + ChunkMultiDraw.report() : "")
                + (Boolean.getBoolean("lab.opt.VertexSortRadixMixin") ? "; " + QuadSort.report() : "")
                + (Boolean.getBoolean("lab.opt.EntityBatchMixin") ? "; " + EntityBatch.report() : "")
                + (Boolean.getBoolean("lab.opt.RenderCacheMixin") ? "; " + RenderCache.report() : "")
                + (Boolean.getBoolean("lab.opt.ParallelEntitiesMixin") ? "; " + ParallelRender.report() : "")
                + (Boolean.getBoolean("lab.opt.PartBatchMixin") ? "; " + PartBatch.report() : "")
                + (Boolean.getBoolean("lab.opt.SectionListsMixin") ? "; " + SectionLists.report() : "")
                + (Boolean.getBoolean("lab.opt.BlockMeshMemoMixin") ? "; " + BlockMeshMemo.report() : "")
                + (Boolean.getBoolean("lab.opt.ClientLightAsyncMixin") ? "; " + ClientLightAsync.report() + "; " + FrameReactor.report() : "")
                + (Boolean.getBoolean("lab.opt.UniformDedupeMixin") ? "; uniform uploads sent " + uniformsSent + ", skipped (same value) " + uniformsSkipped : "")
                + (Boolean.getBoolean("lab.opt.ClientBiomeMemoMixin") && net.minecraft.client.Minecraft.getInstance().level instanceof BiomeMemo bm ? "; " + bm.probe$biomeReport() : "");
    }
}
