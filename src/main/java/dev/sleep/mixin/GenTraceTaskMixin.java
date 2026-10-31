package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.optsupport.GenTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkGenerationTask;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (-Dlab.pgen.trace=true): every run of a generation task (one hop of the worldgen actor). */
@Mixin(ChunkGenerationTask.class)
public abstract class GenTraceTaskMixin {
    @WrapMethod(method = "runUntilWait")
    private CompletableFuture<?> sleep$run(Operation<CompletableFuture<?>> original) {
        long t = System.nanoTime();
        try {
            return original.call();
        } finally {
            GenTrace.taskRuns.increment();
            GenTrace.taskRunNs.add(System.nanoTime() - t);
        }
    }
}
