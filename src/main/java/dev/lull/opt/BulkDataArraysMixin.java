package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.BulkData;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/bulk-arrays (client): the short putBulkData overloads build new float[]{1,1,1,1} and int[]{light x4} per quad
 * (13 % of the render thread's allocation in an entity-heavy scene: items in frames, held items, armour). At these two
 * call sites the full overload is called with arrays reused per thread and refilled every call. Exact as long as the
 * consumer does not keep the arrays (vanilla's reads them at once); opt-in for that reason.
 * <p>
 * 1.20.1: two mixins (this one: items; BulkDataArraysBlocksMixin: blocks) where 1.21.1 has one on both classes - the two
 * renderQuadList have different SRG names, and one mixin's method name cannot map to both. The items' call is Forge's
 * 9-argument overload (alpha, readExistingColor); the reused arrays are BulkData's.
 */
@Mixin(ItemRenderer.class)
public abstract class BulkDataArraysMixin {
    @Redirect(method = "renderQuadList", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIZ)V"))
    private void sleep$items(VertexConsumer c, PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, float a, int light, int overlay, boolean existing) {
        if (BulkData.SHORT9_DEFAULT.get(c.getClass())) c.putBulkData(pose, quad, BulkData.bright(), r, g, b, a, BulkData.light(light), overlay, existing);
        else c.putBulkData(pose, quad, r, g, b, a, light, overlay, existing);   // the consumer overrides it: its own code
    }
}
