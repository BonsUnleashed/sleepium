package dev.lull.mixin;

import dev.lull.optsupport.GenTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dlab.pgen.trace=true): chunks entering the FULL step. */
@Mixin(ChunkStatusTasks.class)
public abstract class GenTraceFullMixin {
    @Inject(method = "full", at = @At("HEAD"))
    private static void sleep$full(WorldGenContext ctx, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                   CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        GenTrace.fulls.increment();
    }
}
