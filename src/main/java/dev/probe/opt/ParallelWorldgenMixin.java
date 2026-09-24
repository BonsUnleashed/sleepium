package dev.probe.opt;

import dev.probe.optsupport.ParallelGen;
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

/**
 * exp/parallel-worldgen: hand SURFACE/CARVERS/FEATURES to ParallelGen's pool instead of running them inside the
 * single worldgen mailbox actor. The returned future makes ChunkGenerationTask yield the actor
 * (runUntilWait → thenRun(runGenerationTask)), so many chunks progress at once.
 */
@Mixin(ChunkMap.class)
public abstract class ParallelWorldgenMixin {
    @Inject(method = "applyStep", at = @At("HEAD"), cancellable = true)
    private void probe$offload(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
                               CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ParallelGen.shouldOffload(step)) {
            ChunkMap self = (ChunkMap) (Object) this;
            cir.setReturnValue(ParallelGen.submit(chunk.getPos(), step, () -> self.applyStep(chunk, step, cache)));
        }
    }
}
