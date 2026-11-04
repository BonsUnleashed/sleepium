package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import dev.lull.GenBenchHooks;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only: when a genbench is active, every generation step of a chunk in its area (1.20.1: a ChunkStatus's
 * generate, run once per chunk and status) records the hash of its window before the step body and after it
 * (GenBenchHooks: per-step exactness), and CARVERS its chunk's hash as on 1.21.1.
 *
 * <p>The "after" hash is taken before the step's future completes to the chunk map (the returned future is replaced by
 * one that hashes first), so no step that waits on this one - a neighbour's FEATURES writing into this chunk - can run
 * in between. With ParallelWorldgenMixin the outer call only hands the step to ParallelGen's pool (its body is not
 * run there); the re-entrant call on the pool, inside the FEATURES job's 3x3 claim, is the one recorded.
 */
@Mixin(ChunkStatus.class)
public abstract class ChunkStepHashMixin {
    @WrapMethod(method = "generate")
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> sleep$hashStep(Executor executor, ServerLevel level, ChunkGenerator generator,
            StructureTemplateManager templates, ThreadedLevelLightEngine light,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> task, List<ChunkAccess> cache,
            Operation<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        ChunkStatus status = (ChunkStatus) (Object) this;
        if (!GenBenchHooks.active() || dev.lull.optsupport.ParallelGen.offloads(status))
            return sleep$carvers(status, original.call(executor, level, generator, templates, light, task, cache));
        if (!GenBenchHooks.recordsStep(status, cache))
            return sleep$carvers(status, original.call(executor, level, generator, templates, light, task, cache));
        ChunkAccess center = cache.get(cache.size() / 2);
        ChunkPos pos = center.getPos();
        long in = GenBenchHooks.windowHash(status, cache, center);
        return original.call(executor, level, generator, templates, light, task, cache).thenApply(r -> {
            r.ifLeft(c -> {
                GenBenchHooks.onStep(status, pos, in, GenBenchHooks.windowHash(status, cache, c));
                GenBenchHooks.onStepDone(status, c);
            });
            return r;
        });
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> sleep$carvers(ChunkStatus status,
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> f) {
        if (status != ChunkStatus.CARVERS || !GenBenchHooks.active() || dev.lull.optsupport.ParallelGen.offloads(status)) return f;
        return f.thenApply(r -> {
            r.ifLeft(c -> GenBenchHooks.onStepDone(status, c));
            return r;
        });
    }
}
