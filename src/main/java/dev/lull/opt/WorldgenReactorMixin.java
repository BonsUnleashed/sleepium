package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lull.optsupport.GenReactor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * WorldgenReactor: the steps of ChunkMap.scheduleChunkGeneration run from GenReactor (priority queues by the holder's
 * queue level, drained by the server's background pool) instead of the `worldgen` mailbox. 1.20.1: each (chunk, status)
 * is one compose (ChunkHolder keeps one future per status, so a step runs once); the compose is vanilla's, only its
 * executor - the worldgen mailbox through ChunkTaskPriorityQueueSorter - is replaced (1.21.1 overwrote
 * runGenerationTask, which 1.20.1 does not have). The executor handed to the step itself (ChunkStatus.generate's first
 * argument, unused by vanilla's steps) stays the mailbox's.
 *
 * <p>Gate: generation code joins two kinds of futures that are computed on the same background pool — a region's
 * old-chunk scan (Blender.of → isOldChunkAround, radius 7 chunks around the step's chunk) and the stronghold ring
 * positions. On a pool without compensation threads every worker could end up joining work queued behind them. So a
 * step whose area needs a scan (or rings) that is not done yet is not run: it is posted again when those futures
 * complete, and its joins then return at once — the same futures, so the same answers; only when the step runs changes.
 */
@Mixin(ChunkMap.class)
public abstract class WorldgenReactorMixin {
    @Shadow @Final private ChunkGeneratorStructureState chunkGeneratorState;

    @Unique private volatile boolean sleep$ringsReady;

    /** Blender's HEIGHT_BLENDING_RANGE_CHUNKS: the radius isOldChunkAround is asked for around a step's chunk */
    @Unique private static final int sleep$BLEND_RADIUS = 7;

    @Unique
    private CompletableFuture<?> sleep$gate(ChunkPos c) {
        List<CompletableFuture<?>> wait = null;
        if (!sleep$ringsReady) {
            for (CompletableFuture<?> f : ((ReactorRingsAccessor) this.chunkGeneratorState).sleep$rings().values())
                if (!f.isDone()) (wait = wait == null ? new ArrayList<>() : wait).add(f);
            if (wait == null) sleep$ringsReady = true;
        }
        if ((Object) this instanceof ReactorStorageAccessor sa && sa.sleep$worker() instanceof ReactorIOWorkerInvoker io) {
            int r = sleep$BLEND_RADIUS;
            int rx0 = (c.x - r) >> 5, rx1 = (c.x + r) >> 5, rz0 = (c.z - r) >> 5, rz1 = (c.z + r) >> 5;
            for (int rx = rx0; rx <= rx1; rx++)
                for (int rz = rz0; rz <= rz1; rz++) {
                    CompletableFuture<?> f = io.sleep$oldData(rx, rz);
                    if (!f.isDone()) (wait = wait == null ? new ArrayList<>() : wait).add(f);
                }
        }
        return wait == null ? null : CompletableFuture.allOf(wait.toArray(new CompletableFuture[0]));
    }

    /** a step's compose posted to GenReactor; run when its gate is open (see class doc) */
    @Unique
    private void sleep$post(ChunkHolder holder, Runnable step) {
        GenReactor.post(holder.getQueueLevel(), () -> {
            CompletableFuture<?> gate = sleep$gate(holder.getPos());
            if (gate != null) {
                GenReactor.GATED.increment();
                gate.whenComplete((v, e) -> this.sleep$post(holder, step));
                return;
            }
            step.run();
        });
    }

    @WrapOperation(method = "scheduleChunkGeneration", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> sleep$reactor(CompletableFuture<?> range, Function<Object, CompletableFuture<?>> compose, Executor mailbox,
                                               Operation<CompletableFuture<?>> original, @Local(argsOnly = true) ChunkHolder holder) {
        return original.call(range, compose, (Executor) step -> this.sleep$post(holder, step));
    }
}
