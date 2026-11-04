package dev.lull.shadermixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.lull.shaders.ShaderPipeline;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A loaded shaderpack's hand and end of frame: the depth clear before the hand is skipped (the pack's composites need
 * the level's depth; depthtex2 keeps it without the hand) and the hand's projection has its depth scaled by
 * MC_HAND_DEPTH (0.125) instead, as Iris draws it; after the hand the composite passes and final run.
 */
@Mixin(GameRenderer.class)
public class ShaderGameRendererMixin {
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private boolean sleep$handDepth(int mask, boolean checkError) {
        if (!ShaderPipeline.active || !ShaderPipeline.current().inLevel) return true;
        ShaderPipeline.current().beforeHand();
        return false;
    }

    @ModifyArg(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"))
    private Matrix4f sleep$handProjection(Matrix4f projection) {
        if (!ShaderPipeline.active || !ShaderPipeline.current().inLevel) return projection;
        return new Matrix4f(projection).scaleLocal(1, 1, 0.125f);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void sleep$endLevel(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ShaderPipeline.active) ShaderPipeline.current().endLevel();
    }
}
