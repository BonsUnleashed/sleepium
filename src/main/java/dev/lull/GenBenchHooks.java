package dev.lull;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/** Thread-safe entry points for mixins (called from worldgen worker threads). */
public final class GenBenchHooks {
    static volatile boolean active;
    static volatile int x0, z0, side;
    /** chunk -> hash after CARVERS */
    static final ConcurrentHashMap<Long, Long> CARVERS = new ConcurrentHashMap<>();

    private GenBenchHooks() {}

    public static boolean active() {
        return active;
    }

    public static void onStepDone(ChunkStep step, ChunkAccess chunk) {
        if (step.targetStatus() != ChunkStatus.CARVERS || !GenBench.HASH_CARVERS) return;
        ChunkPos p = chunk.getPos();
        if (p.x < x0 || p.x >= x0 + side || p.z < z0 || p.z >= z0 + side) return;
        long h = GenBench.hashChunk(chunk);
        // plus the post-processing marks (fluid updates the aquifer asked for): they are not block states
        it.unimi.dsi.fastutil.shorts.ShortList[] pp = chunk.getPostProcessing();
        for (int i = 0; i < pp.length; i++) {
            h = (h ^ (i + 1)) * 1099511628211L;
            if (pp[i] != null) for (int n = 0; n < pp[i].size(); n++) h = (h ^ pp[i].getShort(n)) * 1099511628211L;
        }
        CARVERS.put(p.toLong(), h);
    }
}
