package dev.probe;

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
        if (step.targetStatus() != ChunkStatus.CARVERS) return;
        ChunkPos p = chunk.getPos();
        if (p.x < x0 || p.x >= x0 + side || p.z < z0 || p.z >= z0 + side) return;
        CARVERS.put(p.toLong(), GenBench.hashChunk(chunk));
    }
}
