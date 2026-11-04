package dev.lull.opt;

import dev.lull.optsupport.ClientLightAsync;
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
    private void sleep$beforeTasks(boolean renderLevel, CallbackInfo ci) {
        if (((Minecraft) (Object) this).getPendingTasksCount() > 0) dev.lull.optsupport.FrameReactor.reach(dev.lull.optsupport.FrameReactor.Point.BEFORE_TASKS);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$beforeTick(CallbackInfo ci) {
        dev.lull.optsupport.FrameReactor.reach(dev.lull.optsupport.FrameReactor.Point.BEFORE_TICK);
    }

    // 1.20.1: leaving a world is clearLevel(Screen) (1.21: disconnect(Screen, boolean))
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), require = 0)
    private void sleep$beforeDisconnect(CallbackInfo ci) {
        dev.lull.optsupport.FrameReactor.reach(dev.lull.optsupport.FrameReactor.Point.LEVEL_CHANGE);
    }
}
