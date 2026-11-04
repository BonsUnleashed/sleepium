package dev.lull.shadermixin;

import dev.lull.shaders.ShaderPipeline;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The level render of a loaded shaderpack: its frame starts after vanilla's clear; each profiler section is a phase. */
@Mixin(LevelRenderer.class)
public class ShaderLevelMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", shift = At.Shift.AFTER, ordinal = 0))
    private void sleep$beginLevel(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        // 1.20.1: the view (1.21.1's frustumMatrix) is the PoseStack's top: the camera's rotation, as GameRenderer set it
        if (ShaderPipeline.active) ShaderPipeline.current().beginLevel(camera, poseStack.last().pose(), projectionMatrix, partialTick);
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private String sleep$phase(String name) {
        if (ShaderPipeline.active) ShaderPipeline.current().phase(name);
        return name;
    }
}
