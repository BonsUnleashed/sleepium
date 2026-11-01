package dev.sleep.flwmixin;

import dev.engine_room.flywheel.impl.task.TaskExecutorImpl;
import dev.sleep.optsupport.FlwLabExecutor;
import dev.sleep.optsupport.FrameReactor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flywheel's executor made once (FlwTaskExecutor's lazy holder): the frame's workers (FlwLabExecutor) instead of its own
 * pool of up to 10 threads - unless Flywheel's config asks for no worker threads (its serial executor stays).
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.task.FlwTaskExecutor$AtomicLazy", remap = false)
public abstract class FlwExecutorMixin {
    @Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
    private void sleep$labExecutor(CallbackInfoReturnable<TaskExecutorImpl> cir) {
        if (dev.engine_room.flywheel.impl.FlwConfig.INSTANCE.workerThreads() == 0) return;
        cir.setReturnValue(new FlwLabExecutor(FrameReactor.pool()));
    }
}
