package dev.sleep.mixin;

import dev.sleep.DrawStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsleep.drawStats: time in GameRenderer.renderLevel (the world) and in GameRenderer.render (world + GUI). */
@Mixin(GameRenderer.class)
public abstract class DrawStatsLevelMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$levelBegin(DeltaTracker dt, CallbackInfo ci) {
        DrawStats.phaseBegin(1);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void sleep$levelEnd(DeltaTracker dt, CallbackInfo ci) {
        DrawStats.phaseEnd(1);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sleep$renderBegin(DeltaTracker dt, boolean level, CallbackInfo ci) {
        DrawStats.phaseBegin(2);
        dev.sleep.GpuTimer.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$renderEnd(DeltaTracker dt, boolean level, CallbackInfo ci) {
        DrawStats.phaseEnd(2);
        dev.sleep.GpuTimer.end();
    }
}
