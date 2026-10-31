package dev.sleep.opt;

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
    Matrix4f probe$pose();

    @Accessor("normal")
    Matrix3f probe$normal();

    @Accessor("trustedNormals")
    boolean probe$trusted();

    @Accessor("trustedNormals")
    void probe$setTrusted(boolean v);

    @Invoker("<init>")
    static PoseStack.Pose probe$copyOf(PoseStack.Pose other) {
        throw new AssertionError();
    }
}
