package dev.lull.opt;

import dev.lull.net.NetReactor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NetReactor without MainReactor: before the server thread sleeps (vanilla's waitForTasks) its pending batches go out.
 * With MainReactor on, its waitForTasks overwrite calls NetReactor.beforeSleep itself (this mixin is then off).
 */
@Mixin(MinecraftServer.class)
public abstract class NetReactorWaitMixin {
    @Inject(method = "waitForTasks", at = @At("HEAD"))
    private void sleep$flush(CallbackInfo ci) {
        NetReactor.beforeSleep();
    }
}
