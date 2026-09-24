package dev.probe.mixin;

import dev.probe.GenBenchHooks;
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
 * Measurement only: when a genbench is active, hash each chunk's content right after its CARVERS step, i.e. before
 * any neighbour's FEATURES can write into it. Up to that point generation must be exactly reproducible.
 * (With ParallelWorldgenMixin the outer call is cancelled at HEAD; the re-entrant vanilla call still reaches RETURN.)
 */
@Mixin(ChunkMap.class)
public abstract class ChunkStepHashMixin {
    @Inject(method = "applyStep", at = @At("RETURN"))
    private void probe$hashAfterStep(GenerationChunkHolder chunk, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache,
                                     CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (GenBenchHooks.active()) {
            CompletableFuture<ChunkAccess> f = cir.getReturnValue();
            if (f != null) f.thenAccept(c -> GenBenchHooks.onStepDone(step, c));
        }
    }
}
