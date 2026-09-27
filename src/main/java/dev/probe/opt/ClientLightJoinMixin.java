package dev.probe.opt;

import dev.probe.optsupport.ClientLightAsync;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientLightAsyncMixin: before anything may touch the world or the light engine — queued tasks (packets), the client
 * tick — a running light job is joined (packets only if there are any).
 */
@Mixin(Minecraft.class)
public abstract class ClientLightJoinMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void probe$beforeTasks(boolean renderLevel, CallbackInfo ci) {
        if (ClientLightAsync.running() && ((Minecraft) (Object) this).getPendingTasksCount() > 0) ClientLightAsync.join();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void probe$beforeTick(CallbackInfo ci) {
        ClientLightAsync.join();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"), require = 0)
    private void probe$beforeDisconnect(CallbackInfo ci) {
        ClientLightAsync.join();
    }
}
