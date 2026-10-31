package dev.sleep.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientAuto frame hashes: the lightmap's block light flicker draws Math.random every tick, so two runs never light
 * a block-lit surface alike; while the frame is frozen for its hash the flicker is held at zero.
 */
@Mixin(LightTexture.class)
public abstract class ClientAutoFlickerMixin {
    @Shadow private float blockLightRedFlicker;

    @Inject(method = "tick", at = @At("RETURN"))
    private void probe$noFlicker(CallbackInfo ci) {
        if (dev.sleep.optsupport.ClientCounters.freezeTextures) blockLightRedFlicker = 0f;
    }
}
