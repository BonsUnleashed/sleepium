package dev.probe.opt;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/modelpart-quat (client): translateAndRotate's {@code new Quaternionf().rotationZYX(z, y, x)} for PoseStack.mulPose
 * uses one quaternion per part instead of a new one per call. rotationZYX sets every component, and mulPose multiplies
 * it into the pose at once without keeping it, so the values are the same. A part is used by one thread at a time
 * (the render thread's models, and each parallel-render worker's own copies).
 */
@Mixin(ModelPart.class)
public abstract class ModelPartQuatMixin {
    @Unique private final Quaternionf probe$q = new Quaternionf();

    @Redirect(method = "translateAndRotate", at = @At(value = "NEW", target = "()Lorg/joml/Quaternionf;"))
    private Quaternionf probe$quat() {
        return probe$q;
    }
}
