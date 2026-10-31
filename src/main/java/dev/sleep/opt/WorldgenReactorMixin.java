package dev.sleep.opt;

import dev.sleep.optsupport.GenReactor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * WorldgenReactor: ChunkMap.runGenerationTask posts to GenReactor (priority queues by the holder's queue level, drained
 * by the server's background pool) instead of the `worldgen` mailbox. The body is vanilla's: run the task until it waits,
 * and continue it when the awaited future completes.
 *
 * <p>Gate: generation code joins two kinds of futures that are computed on the same background pool — a region's
 * old-chunk scan (Blender.of → isOldChunkAround, radius 7 chunks) and the stronghold ring positions. On a pool without
 * compensation threads every worker could end up joining work queued behind them. So a task whose area needs a scan (or
 * rings) that is not done yet is not run: it is posted again when those futures complete, and its joins then return
 * at once — the same futures, so the same answers; only when the task runs changes.
 */
@Mixin(ChunkMap.class)
public abstract class WorldgenReactorMixin {
    @Shadow @Final private ChunkGeneratorStructureState chunkGeneratorState;

    @Unique private volatile boolean probe$ringsReady;

    @Unique
    private CompletableFuture<?> probe$gate(ChunkGenerationTask task) {
        List<CompletableFuture<?>> wait = null;
        if (!probe$ringsReady) {
            for (CompletableFuture<?> f : ((ReactorRingsAccessor) this.chunkGeneratorState).probe$rings().values())
                if (!f.isDone()) (wait = wait == null ? new ArrayList<>() : wait).add(f);
            if (wait == null) probe$ringsReady = true;
        }
        if ((Object) this instanceof ReactorStorageAccessor sa && sa.probe$worker() instanceof ReactorIOWorkerInvoker io) {
            ChunkPos c = task.getCenter().getPos();
            int r = ChunkPyramid.GENERATION_PYRAMID.getStepTo(task.targetStatus).getAccumulatedRadiusOf(ChunkStatus.EMPTY) + 7;
            int rx0 = (c.x - r) >> 5, rx1 = (c.x + r) >> 5, rz0 = (c.z - r) >> 5, rz1 = (c.z + r) >> 5;
            for (int rx = rx0; rx <= rx1; rx++)
                for (int rz = rz0; rz <= rz1; rz++) {
                    CompletableFuture<?> f = io.probe$oldData(rx, rz);
                    if (!f.isDone()) (wait = wait == null ? new ArrayList<>() : wait).add(f);
                }
        }
        return wait == null ? null : CompletableFuture.allOf(wait.toArray(new CompletableFuture[0]));
    }

    /**
     * @author probe
     * @reason no mailbox actor between a completed dependency and the next layer (see GenReactor); tasks never wait
     *         inside a worker for data the pool itself has to produce (see class doc)
     */
    @Overwrite
    private void runGenerationTask(ChunkGenerationTask task) {
        GenReactor.post(task.getCenter().getQueueLevel(), () -> {
            CompletableFuture<?> gate = probe$gate(task);
            if (gate != null) {
                GenReactor.GATED.increment();
                gate.whenComplete((v, e) -> this.runGenerationTask(task));
                return;
            }
            CompletableFuture<?> completablefuture = task.runUntilWait();
            if (completablefuture != null) {
                completablefuture.thenRun(() -> this.runGenerationTask(task));
            }
        });
    }
}
