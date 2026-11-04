package dev.lull.mixin;

import com.mojang.datafixers.util.Either;
import dev.lull.optsupport.GenTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dlab.pgen.trace=true): chunks entering the FULL step (1.20.1: ChunkMap.protoChunkToFullChunk). */
@Mixin(ChunkMap.class)
public abstract class GenTraceFullMixin {
    @Inject(method = "protoChunkToFullChunk", at = @At("HEAD"))
    private void sleep$full(ChunkHolder holder, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        GenTrace.fulls.increment();
    }
}
