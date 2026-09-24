package dev.probe.opt;

import dev.probe.optsupport.PersistentPools;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/persistent-pools: stop the engine from re-creating its worker threads.
 * <ul>
 *   <li>Util.makeIoExecutor (IO-Worker-N, Download-N) is {@code Executors.newCachedThreadPool}: 0 core threads,
 *       60 s idle timeout, SynchronousQueue. Every save or IO burst creates new threads, which die 60 s later.
 *       ProcessorMailbox (IOWorker) re-submits itself from the still-busy worker after every task, so with no idle
 *       thread waiting the pool spawns one. Measured: IO-Worker index 4 → 13 over three save-all flushes.</li>
 *   <li>Util.makeExecutor (Worker-Main-N) is a ForkJoinPool with the default 60 s keepAlive, so idle workers retire
 *       and are re-created later (15 → 12 live after idle periods).</li>
 * </ul>
 * Replacement keeps the same thread names, daemon flags and exception handling, but threads are kept alive.
 */
@Mixin(Util.class)
public abstract class PersistentPoolsMixin {
    @Inject(method = "makeIoExecutor", at = @At("HEAD"), cancellable = true)
    private static void probe$persistentIo(String name, boolean daemon, CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue(PersistentPools.io(name, daemon));
    }

    @Inject(method = "makeExecutor", at = @At("HEAD"), cancellable = true)
    private static void probe$persistentBackground(String serviceName, CallbackInfoReturnable<ExecutorService> cir) {
        ExecutorService pool = PersistentPools.background(serviceName);
        if (pool != null) cir.setReturnValue(pool);
    }
}
