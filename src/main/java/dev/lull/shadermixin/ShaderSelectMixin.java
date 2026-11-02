package dev.lull.shadermixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lull.shaders.ShaderPipeline;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** RenderSystem.setShader: during a shaderpack's level render, the pack's program for that vanilla shader and phase. */
@Mixin(RenderSystem.class)
public class ShaderSelectMixin {
    @ModifyExpressionValue(method = "setShader", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"), remap = false)
    private static Object sleep$packShader(Object shader) {
        if (!ShaderPipeline.active) return shader;
        return ShaderPipeline.current().shaderFor((ShaderInstance) shader);
    }
}
