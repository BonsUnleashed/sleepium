package dev.sleep.opt;

import dev.sleep.optsupport.ClientLightAsync;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientLightAsyncMixin: the engine's section-dirty callback, recorded while it runs on the light worker. */
@Mixin(ClientChunkCache.class)
public abstract class ClientLightCallbackMixin {
    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void sleep$record(LightLayer type, SectionPos pos, CallbackInfo ci) {
        if (ClientLightAsync.onWorker()) {
            ClientLightAsync.record(pos);
            ci.cancel();
        }
    }
}
