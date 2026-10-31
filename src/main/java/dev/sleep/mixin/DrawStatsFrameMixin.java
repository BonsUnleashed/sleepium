package dev.sleep.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sleep.DrawStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dprobe.drawStats: time in the buffer swap (waiting for the GPU / driver queue shows up here). */
@Mixin(RenderSystem.class)
public abstract class DrawStatsFrameMixin {
    @Inject(method = "flipFrame", at = @At("HEAD"), remap = false)
    private static void probe$swapBegin(long window, CallbackInfo ci) {
        DrawStats.phaseBegin(0);
    }

    @Inject(method = "flipFrame", at = @At("RETURN"), remap = false)
    private static void probe$swapEnd(long window, CallbackInfo ci) {
        DrawStats.phaseEnd(0);
    }
}
