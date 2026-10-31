package dev.sleep.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleep.DrawStats;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dprobe.drawStats: render-thread time of each block entity render. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class DrawStatsObjectsMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void probe$begin(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.begin(be.getClass());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void probe$end(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        DrawStats.end();
    }
}
