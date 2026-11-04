package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.optsupport.GenTrace;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Measurement only (-Dlab.pgen.trace=true): every run of a generation task (one hop of the worldgen actor; 1.20.1:
 * a task of the `worldgen` ProcessorMailbox, 1.21.1's ChunkGenerationTask.runUntilWait).
 */
@Mixin(ProcessorMailbox.class)
public abstract class GenTraceTaskMixin {
    @Shadow @Final private String name;

    @WrapMethod(method = "pollTask")
    private boolean sleep$run(Operation<Boolean> original) {
        if (!"worldgen".equals(this.name)) return original.call();
        long t = System.nanoTime();
        boolean ran = false;
        try {
            return ran = original.call();
        } finally {
            if (ran) {
                GenTrace.taskRuns.increment();
                GenTrace.taskRunNs.add(System.nanoTime() - t);
            }
        }
    }
}
