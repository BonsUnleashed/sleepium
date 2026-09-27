package dev.probe.mixin;

import dev.probe.DrawStats;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dprobe.drawStats: time in GameRenderer.renderLevel (the world) and in GameRenderer.render (world + GUI). */
@Mixin(GameRenderer.class)
public abstract class DrawStatsLevelMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void probe$levelBegin(DeltaTracker dt, CallbackInfo ci) {
        DrawStats.phaseBegin(1);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void probe$levelEnd(DeltaTracker dt, CallbackInfo ci) {
        DrawStats.phaseEnd(1);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void probe$renderBegin(DeltaTracker dt, boolean level, CallbackInfo ci) {
        DrawStats.phaseBegin(2);
        dev.probe.GpuTimer.begin();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void probe$renderEnd(DeltaTracker dt, boolean level, CallbackInfo ci) {
        DrawStats.phaseEnd(2);
        dev.probe.GpuTimer.end();
    }
}
