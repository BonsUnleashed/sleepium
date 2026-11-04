package dev.lull.mixin;

import com.mojang.datafixers.util.Either;
import dev.lull.optsupport.GenTrace;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measurement only (-Dlab.pgen.trace=true): wall time from a step's start to its future's completion, per status
 * (1.20.1: ChunkStatus.generate; 1.21.1 ChunkMap.applyStep). An offloaded step counts once, from its hand-off.
 */
@Mixin(ChunkStatus.class)
public abstract class GenTraceStepMixin {
    @Inject(method = "generate", at = @At("RETURN"))
    private void sleep$step(Executor executor, ServerLevel level, ChunkGenerator generator, StructureTemplateManager templates, ThreadedLevelLightEngine light,
                            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> task, List<ChunkAccess> cache,
                            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        ChunkStatus self = (ChunkStatus) (Object) this;
        if (dev.lull.optsupport.ParallelGen.ENABLED && !dev.lull.optsupport.ParallelGen.shouldOffload(self)
                && (self == ChunkStatus.SURFACE || self == ChunkStatus.CARVERS || self == ChunkStatus.FEATURES)) return;   // the re-entrant call on the pool
        CompletableFuture<?> f = cir.getReturnValue();
        if (f == null) return;
        String status = BuiltInRegistries.CHUNK_STATUS.getKey(self).getPath();
        if (f.isDone()) {
            GenTrace.step(status, 0);
            return;
        }
        long t0 = System.nanoTime();
        f.whenComplete((c, t) -> GenTrace.step(status, System.nanoTime() - t0));
    }
}
