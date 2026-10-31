package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Measurement only (-Dlab.pgen.trace=true): a synchronous getChunk that must wait for the chunk (vanilla's managedBlock
 * on the server thread) as a profiler section "blockingGetChunk", so a slow tick's tree shows who waited for a chunk.
 */
@Mixin(ServerChunkCache.class)
public abstract class GenTraceBlockingMixin {
    @Shadow @Final ServerLevel level;

    @WrapOperation(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private void sleep$blocking(@org.spongepowered.asm.mixin.injection.Coerce BlockableEventLoop<?> executor, BooleanSupplier done, Operation<Void> original) {
        level.getProfiler().push("blockingGetChunk");
        long t = System.nanoTime();
        try {
            original.call(executor, done);
        } finally {
            level.getProfiler().pop();
            long ms = (System.nanoTime() - t) / 1_000_000L;
            if (ms >= 20) {   // who asked: the first frames above the chunk cache
                StringBuilder sb = new StringBuilder("[sleep/blocking] " + ms + " ms:");
                int n = 0;
                for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                    String c = e.getClassName();
                    if (c.startsWith("java.") || c.contains("ServerChunkCache") || c.contains("GenTraceBlocking") || c.contains("BlockableEventLoop")) continue;
                    sb.append(System.lineSeparator()).append("   ").append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
                    if (++n >= 9) break;
                }
                System.out.println(sb);
            }
        }
    }
}
