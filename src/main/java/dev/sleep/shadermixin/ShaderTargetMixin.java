package dev.sleep.shadermixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.sleep.shaders.ShaderPipeline;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a shaderpack's level render, binding the main target binds the pack's framebuffer. */
@Mixin(RenderTarget.class)
public class ShaderTargetMixin {
    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void sleep$packTarget(boolean setViewport, CallbackInfo ci) {
        if (!ShaderPipeline.active) return;
        ShaderPipeline p = ShaderPipeline.current();
        if (!p.inLevel || (Object) this != Minecraft.getInstance().getMainRenderTarget()) return;
        p.bindGbuffers(new int[] {0});
        ci.cancel();
    }
}
