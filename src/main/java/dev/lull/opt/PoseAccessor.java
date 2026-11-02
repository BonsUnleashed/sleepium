package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** PoseStack.Pose internals for PoseStackPoolMixin. */
@Mixin(PoseStack.Pose.class)
public interface PoseAccessor {
    @Accessor("pose")
    Matrix4f sleep$pose();

    @Accessor("normal")
    Matrix3f sleep$normal();

    @Accessor("trustedNormals")
    boolean sleep$trusted();

    @Accessor("trustedNormals")
    void sleep$setTrusted(boolean v);

    @Invoker("<init>")
    static PoseStack.Pose sleep$copyOf(PoseStack.Pose other) {
        throw new AssertionError();
    }
}
