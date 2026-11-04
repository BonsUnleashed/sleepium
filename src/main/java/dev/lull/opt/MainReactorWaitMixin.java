package dev.lull.opt;

import dev.lull.optsupport.MainReactor;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MainReactor on 1.20.1: the idle park. 1.21.1 overwrites MinecraftServer.waitForTasks (idle: MainReactor.park until
 * the deadline or a signal; otherwise vanilla's yield + 100 µs park). 1.20.1's MinecraftServer does not override
 * waitForTasks, BlockableEventLoop's runs (Thread.yield, then parkNanos 100 µs): its park is replaced for the server
 * while idle, everything else keeps vanilla's. Left as in vanilla 1.20.1: the yield before the park.
 */
@Mixin(BlockableEventLoop.class)
public abstract class MainReactorWaitMixin {
    @Redirect(method = "waitForTasks", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/LockSupport;parkNanos(Ljava/lang/Object;J)V"))
    private void sleep$park(Object blocker, long nanos) {
        if (MainReactor.idle && (Object) this instanceof MinecraftServer && (Object) this instanceof MainReactor.Server s) {
            MainReactor.park(s.sleep$deadlineNanos(), s.sleep$queues());
        } else {
            LockSupport.parkNanos(blocker, nanos);
        }
    }
}
