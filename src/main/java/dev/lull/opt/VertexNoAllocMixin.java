package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/frame-garbage (client): VertexConsumer's matrix helpers (addVertex with a Matrix4f, setNormal with a pose) make a new vector for every vertex
 * of every renderer that passes a matrix - BufferBuilder's are the same JOML calls into one vector of the builder (one
 * thread writes a builder at a time; the values go on at once).
 */
@Mixin(BufferBuilder.class)
public abstract class VertexNoAllocMixin implements VertexConsumer {
    @Unique private final org.joml.Vector3f sleep$v = new org.joml.Vector3f();

    /** VertexConsumer's default, the same JOML call into the builder's own vector */
    @Override
    public VertexConsumer addVertex(org.joml.Matrix4f pose, float x, float y, float z) {
        org.joml.Vector3f v = pose.transformPosition(x, y, z, sleep$v);
        return this.addVertex(v.x(), v.y(), v.z());
    }

    /** VertexConsumer's default, the same JOML call into the builder's own vector */
    @Override
    public VertexConsumer setNormal(com.mojang.blaze3d.vertex.PoseStack.Pose pose, float normalX, float normalY, float normalZ) {
        org.joml.Vector3f v = pose.transformNormal(normalX, normalY, normalZ, sleep$v);
        return this.setNormal(v.x(), v.y(), v.z());
    }
}
