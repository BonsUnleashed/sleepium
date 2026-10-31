package dev.sleep.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.PartBatch;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/part-batch (client, render cache v3): a model part's compile becomes a queued record (pose, light, overlay,
 * colour) drawn from its VRAM geometry, when PartBatch can take it; otherwise the vanilla compile.
 */
@Mixin(ModelPart.class)
public abstract class PartBatchMixin implements PartBatch.PartCompiler {
    @Shadow
    private void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        throw new AssertionError();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void sleep$compile(ModelPart self, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
        if (!PartBatch.capture(self, pose, buffer, light, overlay, color, this)) compile(pose, buffer, light, overlay, color);
    }

    @Override
    public void sleep$geometry(PoseStack.Pose pose, VertexConsumer consumer) {
        compile(pose, consumer, 0, 0, -1);
    }
}
