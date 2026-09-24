package dev.probe.mixin;

import dev.probe.TaskStats;
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
    private TaskStats.Loop probe$stats;

    @Inject(method = "tell", at = @At("HEAD"))
    private void probe$onTell(Runnable task, CallbackInfo ci) {
        TaskStats.Loop s = probe$stats;
        if (s == null) {
            s = probe$stats = TaskStats.loop(name());
        }
        (isSameThread() ? s.tellsFromOwner : s.tellsFromOther).increment();
    }
}
