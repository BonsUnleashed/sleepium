package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (genbench): event-loop waits (waitForTasks) on the server thread, outside and inside the idle phase. */
@Mixin(BlockableEventLoop.class)
public abstract class GenProfileWaitMixin {
    /** event-to-wake latency of the server reactor: a task posted to an event loop while the server thread is parked */
    @Inject(method = "tell", at = @At("HEAD"))
    private void probe$event(Runnable task, CallbackInfo ci) {
        dev.sleep.optsupport.MainReactor.noteEvent();
    }

    @WrapMethod(method = "waitForTasks")
    private void probe$wait(Operation<Void> original) {
        if (!GenProfile.active() || !Thread.currentThread().getName().startsWith("Server thread")) { original.call(); return; }
        long t = System.nanoTime();
        original.call();
        GenProfile.add("main: wait, " + (dev.sleep.optsupport.MainReactor.idle ? "idle phase" : "blocking") + ", chunk executor", System.nanoTime() - t, 0);
    }
}
