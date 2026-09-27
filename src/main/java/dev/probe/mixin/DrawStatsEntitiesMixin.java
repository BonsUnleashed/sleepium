package dev.probe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.probe.DrawStats;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dprobe.drawStats: render-thread time of each entity render, and the frame count. */
@Mixin(LevelRenderer.class)
public abstract class DrawStatsEntitiesMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void probe$begin(Entity e, double x, double y, double z, float pt, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.begin(e.getClass());
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void probe$end(Entity e, double x, double y, double z, float pt, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.end();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void probe$frame(CallbackInfo ci) {
        DrawStats.frame();
    }
}
