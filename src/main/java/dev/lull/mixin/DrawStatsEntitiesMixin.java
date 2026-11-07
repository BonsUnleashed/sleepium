package dev.lull.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.DrawStats;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dsleep.drawStats: render-thread time of each entity render, and the frame count. Test only
 * (-Dsleep.test.skipDraw=ItemEntity,ExperienceOrb): entities of these classes are not drawn - the ceiling of what a
 * faster way of drawing them could give (their sort and upload at the batch's end gone too).
 */
@Mixin(LevelRenderer.class)
public abstract class DrawStatsEntitiesMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void sleep$begin(Entity e, double x, double y, double z, float pt, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.begin(e.getClass());
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void sleep$end(Entity e, double x, double y, double z, float pt, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.end();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$frame(CallbackInfo ci) {
        DrawStats.frame();
    }
}
