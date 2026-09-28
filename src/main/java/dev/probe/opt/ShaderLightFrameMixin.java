package dev.probe.opt;

import dev.probe.optsupport.LightVolumes;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ShaderLightMixin: the light volumes of sections whose light changed, refreshed before the terrain is drawn. */
@Mixin(LevelRenderer.class)
public abstract class ShaderLightFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void probe$volumes(CallbackInfo ci) {
        LightVolumes.frame();
    }
}
