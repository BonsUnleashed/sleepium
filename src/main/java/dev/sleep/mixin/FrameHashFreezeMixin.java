package dev.sleep.mixin;

import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame-hash runs only (-Dsleep.clientAuto.framehash): animated textures stop while the frame is captured. */
@Mixin(TextureManager.class)
public abstract class FrameHashFreezeMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sleep$freeze(CallbackInfo ci) {
        if (dev.sleep.optsupport.ClientCounters.freezeTextures) ci.cancel();
    }
}
