package dev.sleep.mixin;

import dev.sleep.TaskStats;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts tasks handed to an event loop (server thread, render thread, chunk-source main executor). */
@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {
    @Shadow
    public abstract String name();

    @Shadow
    public abstract boolean isSameThread();

    @Unique
    private TaskStats.Loop sleep$stats;

    @Inject(method = "tell", at = @At("HEAD"))
    private void sleep$onTell(Runnable task, CallbackInfo ci) {
        TaskStats.Loop s = sleep$stats;
        if (s == null) {
            s = sleep$stats = TaskStats.loop(name());
        }
        (isSameThread() ? s.tellsFromOwner : s.tellsFromOther).increment();
    }
}
