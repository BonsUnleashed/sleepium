package dev.lull.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.DrawStats;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsleep.drawStats: render-thread time of each block entity render. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class DrawStatsObjectsMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sleep$begin(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.begin(be.getClass());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$end(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.end();
    }
}
