package dev.lull.opt;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.LightVolumes;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ShaderLightMixin: fluid vertices get the fluid light recipe — the light of each face comes from
 * getLightColor(level, p) (per channel max of p and p.above()) computed just before its vertices. Vanilla's and
 * mods' fluids that render through LiquidBlockRenderer; anything else keeps the sentinel (baked).
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class ShaderLightFluidMixin {
    @Shadow
    private int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        return 0;
    }

    @Shadow
    private void vertex(VertexConsumer buffer, float x, float y, float z, float r, float g, float b, float alpha, float u, float v, int light) {}

    @Redirect(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int sleep$light(LiquidBlockRenderer self, BlockAndTintGetter level, BlockPos p, @Local(argsOnly = true, ordinal = 0) BlockPos fluidPos) {
        LightVolumes.fluidLight(fluidPos, p);
        return getLightColor(level, p);
    }

    @Redirect(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFI)V"))
    private void sleep$vertex(LiquidBlockRenderer self, VertexConsumer buffer, float x, float y, float z, float r, float g, float b, float alpha, float u, float v, int light,
                              @Local(argsOnly = true) BlockAndTintGetter level, @Local(argsOnly = true, ordinal = 0) BlockPos fluidPos) {
        vertex(buffer, x, y, z, r, g, b, alpha, u, v, light);
        LightVolumes.fluidVertex(level, fluidPos, buffer);
    }
}
