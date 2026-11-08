package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/frame-garbage (client): VertexConsumer's matrix helpers (vertex with a Matrix4f, normal with a Matrix3f) make a new vector for every vertex
 * of every renderer that passes a matrix - BufferBuilder's are the same JOML calls into one vector of the builder (one
 * thread writes a builder at a time; the values go on at once).
 */
@Mixin(BufferBuilder.class)
public abstract class VertexNoAllocMixin implements VertexConsumer {
    @Unique private final org.joml.Vector3f sleep$v = new org.joml.Vector3f();
    @Unique private final org.joml.Vector4f sleep$v4 = new org.joml.Vector4f();

    /** VertexConsumer's default, the same JOML call into the builder's own vector */
    @Override
    public VertexConsumer vertex(org.joml.Matrix4f matrix, float x, float y, float z) {
        org.joml.Vector4f v = matrix.transform(sleep$v4.set(x, y, z, 1.0F));
        return this.vertex((double) v.x(), (double) v.y(), (double) v.z());
    }

    /** VertexConsumer's default, the same JOML call into the builder's own vector */
    @Override
    public VertexConsumer normal(org.joml.Matrix3f matrix, float x, float y, float z) {
        org.joml.Vector3f v = matrix.transform(sleep$v.set(x, y, z));
        return this.normal(v.x(), v.y(), v.z());
    }
}
