package dev.lull.opt;

import dev.lull.optsupport.LabPools;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/lab-pools: vanilla's background and IO pools replaced by LabRuntime.
 * <ul>
 *   <li>Util.makeExecutor ("Worker-Main"): ForkJoinPool(cores - 1) with 60 s keepAlive, so workers retire and are
 *       re-created → LabRuntime with a fixed small set of persistent workers (the server entry point's pool).</li>
 *   <li>Util.makeIoExecutor ("IO-Worker-", "Download-"): newCachedThreadPool, which creates a thread whenever no idle
 *       one is waiting (ProcessorMailbox re-submits itself from the busy worker) → LabRuntime at ABOVE_NORMAL.</li>
 * </ul>
 * Uncaught exceptions still go to vanilla's Util.onThreadException (crash reports for ReportedException).
 * Takes precedence over PersistentPoolsMixin (see OptMixinPlugin).
 */
@Mixin(Util.class)
public abstract class LabPoolsMixin {
    @Shadow
    private static void onThreadException(Thread thread, Throwable throwable) {}

    @Inject(method = "makeExecutor", at = @At("HEAD"), cancellable = true)
    private static void sleep$labBackground(String serviceName, CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue(LabPools.background(serviceName, (t, e) -> onThreadException(t, e)));
    }

    @Inject(method = "makeIoExecutor", at = @At("HEAD"), cancellable = true)
    private static void sleep$labIo(String name, boolean daemon, CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue(LabPools.io(name, daemon, (t, e) -> onThreadException(t, e)));
    }
}
