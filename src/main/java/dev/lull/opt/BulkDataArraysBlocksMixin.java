package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.BulkData;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * BulkDataArraysMixin's block half (1.20.1: its own mixin, see there): the flat-lit block path's short putBulkData,
 * vanilla's 7-argument overload (no alpha; its default goes to the long overload without alpha, which this calls too).
 */
@Mixin(ModelBlockRenderer.class)
public abstract class BulkDataArraysBlocksMixin {
    @Redirect(method = "renderQuadList", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFII)V"))
    private static void sleep$blocks(VertexConsumer c, PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, int light, int overlay) {
        if (BulkData.SHORT7_DEFAULT.get(c.getClass())) c.putBulkData(pose, quad, BulkData.bright(), r, g, b, BulkData.light(light), overlay, false);
        else c.putBulkData(pose, quad, r, g, b, light, overlay);
    }
}
