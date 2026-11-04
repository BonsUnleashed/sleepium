package dev.lull.optsupport;

import java.util.Locale;

/** State and counters of the client-side optimisations (render thread unless noted). */
public final class ClientCounters {
    /** other mods' counters in the client report */
    public static final java.util.List<java.util.function.Supplier<String>> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static String extra() {
        StringBuilder sb = new StringBuilder();
        for (java.util.function.Supplier<String> x : EXTRA) sb.append("; ").append(x.get());
        return sb.toString();
    }

    public static long uniformsSent, uniformsSkipped;

    public static long biomeSkips, samplerHits;
    /** frame-hash runs: animated textures stop (FrameHashFreezeMixin) */
    public static volatile boolean freezeTextures;
    /** changes whenever the set of loaded chunks or their biomes may have changed (AmbientChunksMixin) */
    public static long ambientVersion;

    /** Implemented by ClientChunkCache (AmbientChunksMixin). */
    public interface AmbientChunks {
        boolean sleep$anyAmbient();
    }

    private ClientCounters() {}

    public static String report() {
        // 1.20.1: the event sites' report (the event bus step) and the renderer's (ChunkMultiDraw, QuadSort, EntityBatch,
        // RenderCache, ParallelRender, PartBatch / Materials, SectionLists, BlockMeshMemo, LightVolumes: stage 3) come
        // over with their parts (port-pending.txt)
        return String.format(Locale.ROOT, "client opts: ambient biome lookups skipped %d, sampler locations from cache %d; collision memo hits %d, misses %d",
                biomeSkips, samplerHits, BlockCollisionMemo.hits, BlockCollisionMemo.misses) + extra()
                + (Boolean.getBoolean("lab.opt.ChunkTickMemoMixin") ? "; chunk tick memo: hits " + ChunkTickMemo.hits + ", misses " + ChunkTickMemo.misses + ", epoch " + ChunkTickMemo.epoch : "")
                + (Boolean.getBoolean("lab.opt.ClientLightAsyncMixin") ? "; " + ClientLightAsync.report() + "; " + FrameReactor.report() : "")
                + (Boolean.getBoolean("lab.opt.UniformDedupeMixin") ? "; uniform uploads sent " + uniformsSent + ", skipped (same value) " + uniformsSkipped : "")
                + (Boolean.getBoolean("lab.opt.ClientBiomeMemoMixin") && net.minecraft.client.Minecraft.getInstance().level instanceof BiomeMemo bm ? "; " + bm.sleep$biomeReport() : "");
    }
}
