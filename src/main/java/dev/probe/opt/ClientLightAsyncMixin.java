package dev.probe.opt;

import dev.probe.optsupport.ClientLightAsync;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/client-light-async (client): renderLevel's light updates on a worker that may span several frames. At the light
 * point: a job still running with no queued light tasks is left running (nothing else adds light work between ticks and
 * packets), otherwise it is joined first. At the end of renderLevel a finished job's section marks are replayed
 * without waiting. Ticks, packets and level changes join first (ClientLightJoinMixin).
 */
@Mixin(LevelRenderer.class)
public abstract class ClientLightAsyncMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"))
    private void probe$poll(ClientLevel level) {
        if (ClientLightAsync.running() && ((ClientLevelLightQueueAccessor) level).probe$lightQueue().isEmpty()) {
            ClientLightAsync.skipNext = true;   // the running job has all the work there is
            ClientLightAsync.skipped++;
            return;
        }
        ClientLightAsync.join();
        ClientLightAsync.skipNext = false;
        level.pollLightUpdates();
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"))
    private int probe$async(LevelLightEngine engine) {
        if (ClientLightAsync.skipNext) {
            ClientLightAsync.skipNext = false;
            return 0;
        }
        ClientLightAsync.start(engine);
        return 0;   // vanilla ignores the count
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void probe$replay(CallbackInfo ci) {
        ClientLightAsync.replayIfDone();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void probe$joinOnLevelChange(CallbackInfo ci) {
        ClientLightAsync.join();
    }
}
