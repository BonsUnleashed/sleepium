package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.PartBatch;
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
    private void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        throw new AssertionError();
    }

    /** (1.20.1: the colour as four floats; the record takes it packed to bytes as BufferBuilder writes them) */
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void sleep$compile(ModelPart self, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, float red, float green, float blue, float alpha) {
        if (!PartBatch.capture(self, pose, buffer, light, overlay, PartBatch.packColor(red, green, blue, alpha), this))
            compile(pose, buffer, light, overlay, red, green, blue, alpha);
    }

    @Override
    public void sleep$geometry(PoseStack.Pose pose, VertexConsumer consumer) {
        compile(pose, consumer, 0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
