package dev.sleep.mixin;

import dev.sleep.optsupport.GenTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dlab.pgen.trace=true): wall time from a step's start to its future's completion, per status. */
@Mixin(ChunkMap.class)
public abstract class GenTraceStepMixin {
    @Inject(method = "applyStep", at = @At("RETURN"))
    private void sleep$step(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
                            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        CompletableFuture<ChunkAccess> f = cir.getReturnValue();
        if (f == null) return;
        String status = step.targetStatus().getName();
        if (f.isDone()) {
            GenTrace.step(status, 0);
            return;
        }
        long t0 = System.nanoTime();
        f.whenComplete((c, t) -> GenTrace.step(status, System.nanoTime() - t0));
    }
}
