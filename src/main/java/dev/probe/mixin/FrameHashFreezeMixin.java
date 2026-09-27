package dev.probe.mixin;

import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame-hash runs only (-Dprobe.clientAuto.framehash): animated textures stop while the frame is captured. */
@Mixin(TextureManager.class)
public abstract class FrameHashFreezeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void probe$freeze(CallbackInfo ci) {
        if (dev.probe.optsupport.ClientCounters.freezeTextures) ci.cancel();
    }
}
