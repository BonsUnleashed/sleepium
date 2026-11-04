package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.lull.optsupport.FullRing;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * WorldgenReactor: the FULL step's executor puts its main-thread task into FullRing instead of the main mailbox.
 * 1.20.1: protoChunkToFullChunk's one thenApplyAsync takes the executor (a lambda telling mainThreadMailbox through
 * the priority sorter by the holder's ticket level); FullRing buckets by that same level.
 */
@Mixin(ChunkMap.class)
public abstract class ReactorFullMixin {
    @WrapOperation(method = "protoChunkToFullChunk", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<?> sleep$ring(CompletableFuture<?> f, Function<Object, Object> fn, Executor mailbox, Operation<CompletableFuture<?>> original,
                                            @Local(argsOnly = true) ChunkHolder holder) {
        return original.call(f, fn, (Executor) task -> FullRing.offer(holder.getTicketLevel(), task));
    }
}
