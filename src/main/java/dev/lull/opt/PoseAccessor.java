package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** PoseStack.Pose internals for PoseStackPoolMixin (1.20.1: no trustedNormals, the constructor takes the two matrices). */
@Mixin(PoseStack.Pose.class)
public interface PoseAccessor {
    @Accessor("pose")
    Matrix4f sleep$pose();

    @Accessor("normal")
    Matrix3f sleep$normal();

    @Invoker("<init>")
    static PoseStack.Pose sleep$create(Matrix4f pose, Matrix3f normal) {
        throw new AssertionError();
    }
}
