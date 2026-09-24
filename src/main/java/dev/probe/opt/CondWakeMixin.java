package dev.probe.opt;

import dev.probe.optsupport.WakeFlags;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/eventloop-condwake: wake the loop's thread only if it announced it is parking.
 *
 * <p>Vanilla {@code tell()} calls {@code LockSupport.unpark} for every task (a SetEvent syscall on Windows),
 * even while the thread is busy running the tick. Here:
 * <ul>
 *   <li>{@code waitForTasks()} HEAD: flag = true (armed); RETURN: flag = false</li>
 *   <li>{@code tell()}: unpark only if {@code flag.getAndSet(false)}</li>
 * </ul>
 * A wake-up can be lost if a producer reads the flag just before the consumer arms it. That is bounded: vanilla
 * waitForTasks parks with a timeout (100 us, which is about 1 ms on Windows), and the loop polls again afterwards.
 * So the worst case is the vanilla polling latency, never a hang.
 */
@Mixin(BlockableEventLoop.class)
public abstract class CondWakeMixin {
    @Redirect(method = "tell", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/LockSupport;unpark(Ljava/lang/Thread;)V"))
    private void probe$unparkOnlyIfParked(Thread thread) {
        AtomicBoolean flag = WakeFlags.of(thread);
        if (flag == null || flag.getAndSet(false)) {
            LockSupport.unpark(thread);
            WakeFlags.UNPARKED.increment();
        } else {
            WakeFlags.SKIPPED.increment();
        }
    }

    @Inject(method = "waitForTasks", at = @At("HEAD"))
    private void probe$arm(CallbackInfo ci) {
        WakeFlags.current().set(true);
    }

    @Inject(method = "waitForTasks", at = @At("RETURN"))
    private void probe$disarm(CallbackInfo ci) {
        WakeFlags.current().set(false);
    }
}
