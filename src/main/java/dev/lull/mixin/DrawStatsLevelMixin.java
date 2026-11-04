package dev.lull.mixin;

import dev.lull.DrawStats;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dsleep.drawStats: time in GameRenderer.renderLevel (the world) and in GameRenderer.render (world + GUI). 1.20.1: their
 * partial tick and frame time as arguments (1.21.1: a DeltaTracker).
 */
@Mixin(GameRenderer.class)
public abstract class DrawStatsLevelMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$levelBegin(float partialTicks, long finishTimeNano, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        DrawStats.phaseBegin(1);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void sleep$levelEnd(float partialTicks, long finishTimeNano, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        DrawStats.phaseEnd(1);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sleep$renderBegin(float partialTicks, long nanoTime, boolean level, CallbackInfo ci) {
        DrawStats.phaseBegin(2);
        dev.lull.GpuTimer.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$renderEnd(float partialTicks, long nanoTime, boolean level, CallbackInfo ci) {
        DrawStats.phaseEnd(2);
        dev.lull.GpuTimer.end();
    }
}
