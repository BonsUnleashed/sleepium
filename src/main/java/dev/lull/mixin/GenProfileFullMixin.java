package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.GenProfile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): the whole main-thread body of the FULL step (new LevelChunk … ChunkEvent.Load).
 * 1.20.1: protoChunkToFullChunk's one thenApplyAsync - its function is timed (1.21.1 wrapped ChunkStatusTasks' lambda;
 * lambda names are not stable across 1.20.1's dev and SRG names, the call is). Without a genbench the function is
 * passed through unchanged.
 */
@Mixin(ChunkMap.class)
public abstract class GenProfileFullMixin {
    @WrapOperation(method = "protoChunkToFullChunk", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> sleep$full(CompletableFuture<?> f, Function<Object, Object> fn, Executor executor,
                                            Operation<CompletableFuture<?>> original) {
        if (!GenProfile.active()) return original.call(f, fn, executor);
        Function<Object, Object> timed = x -> {
            long t = System.nanoTime(), m = GenProfile.allocated();
            try {
                return fn.apply(x);
            } finally {
                GenProfile.add("main: FULL body", System.nanoTime() - t, GenProfile.allocated() - m);
            }
        };
        return original.call(f, timed, executor);
    }
}
