package dev.probe.opt;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.probe.optsupport.LightVolumes;
import java.util.BitSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/shader-light: after each quad of the AO and flat face paths is written, its vertices get their light recipe
 * (LightVolumes.quadWritten) while the section is compiled with recipes. No allocation per quad (redirects).
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ShaderLightMixin {
    private static final String PUT = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;putQuadData(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIIII)V";

    @Shadow
    private void putQuadData(BlockAndTintGetter level, BlockState state, BlockPos pos, VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                             float b0, float b1, float b2, float b3, int l0, int l1, int l2, int l3, int overlay) {}

    @Redirect(method = "renderModelFaceAO", at = @At(value = "INVOKE", target = PUT))
    private void probe$ao(ModelBlockRenderer self, BlockAndTintGetter level, BlockState state, BlockPos pos, VertexConsumer consumer, PoseStack.Pose pose,
                          BakedQuad quad, float b0, float b1, float b2, float b3, int l0, int l1, int l2, int l3, int overlay,
                          @Local(argsOnly = true) BitSet shapeFlags) {
        putQuadData(level, state, pos, consumer, pose, quad, b0, b1, b2, b3, l0, l1, l2, l3, overlay);
        LightVolumes.quadWritten(level, state, pos, quad, shapeFlags, consumer, quad.hasAmbientOcclusion());
    }

    @Redirect(method = "renderModelFaceFlat", at = @At(value = "INVOKE", target = PUT))
    private void probe$flat(ModelBlockRenderer self, BlockAndTintGetter level, BlockState state, BlockPos pos, VertexConsumer consumer, PoseStack.Pose pose,
                            BakedQuad quad, float b0, float b1, float b2, float b3, int l0, int l1, int l2, int l3, int overlay,
                            @Local(argsOnly = true) BitSet shapeFlags, @Local(argsOnly = true) boolean repackLight) {
        putQuadData(level, state, pos, consumer, pose, quad, b0, b1, b2, b3, l0, l1, l2, l3, overlay);
        LightVolumes.quadWritten(level, state, pos, quad, shapeFlags, consumer, false, !repackLight);
    }
}
