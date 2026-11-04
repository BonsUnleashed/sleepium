package dev.lull.opt;

import dev.lull.net.NetReactor;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NetReactor: before the server thread sleeps (waitForTasks) its pending batches go out. 1.21.1 hooks
 * MinecraftServer.waitForTasks and is off with MainReactor (whose overwrite of it flushes); 1.20.1's MinecraftServer has
 * no waitForTasks of its own - BlockableEventLoop's runs, and MainReactorWaitMixin only replaces its park - so this hook
 * is on BlockableEventLoop, with or without MainReactor. NetReactor.beforeSleep acts on the server thread only (the
 * client's and the chunk executors' waits pass through it at the cost of a thread compare).
 */
@Mixin(BlockableEventLoop.class)
public abstract class NetReactorWaitMixin {
    @Inject(method = "waitForTasks", at = @At("HEAD"))
    private void sleep$flush(CallbackInfo ci) {
        NetReactor.beforeSleep();
    }
}
