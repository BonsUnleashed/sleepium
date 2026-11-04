package dev.lull.opt;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MainReactor: light updates are started by the server thread's polling (MainThreadExecutor.pollTask →
 * tryScheduleUpdate) unless 1000 tasks queue up. With a sleeping server thread that would delay light (and the
 * generation waiting for it), so light schedules itself: a queued light task schedules an update, and an update that
 * leaves work behind (tasks that arrived while it ran, beyond its 1000) schedules the next one. tryScheduleUpdate is
 * thread-safe (a CAS on `scheduled`) and a no-op when there is nothing to do.
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class MainReactorLightMixin {
    @Shadow
    public abstract void tryScheduleUpdate();

    // lambda names with descriptors: Forge 1.20.1 runs them under SRG names, the refmap maps them by signature
    @Inject(method = "lambda$addTask$15(Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V", at = @At("TAIL"))
    private void sleep$schedule(CallbackInfo ci) {
        this.tryScheduleUpdate();
    }

    @Inject(method = "lambda$tryScheduleUpdate$27()V", at = @At("TAIL"))
    private void sleep$again(CallbackInfo ci) {
        this.tryScheduleUpdate();
    }
}
