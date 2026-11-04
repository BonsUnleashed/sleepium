package dev.lull.shadermixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ShaderIds: the builder's vertices as the terrain ids need them - its buffer, the write offset after the last vertex,
 * the batch's vertex count, format, whether it is building. The pipeline's own accessor (1.21.1 read them through the
 * render cache's BufferBuilderAccessor, which is applied only with the engine's flags: a pack with them off crashed in
 * the section compile); names of its own, so the accessors can apply together.
 */
@Mixin(BufferBuilder.class)
public interface ShaderBuilderAccessor {
    @Accessor("buffer")
    ByteBuffer sleep$shBuffer();

    @Accessor("nextElementByte")
    int sleep$shNext();

    @Accessor("vertices")
    int sleep$shVertices();

    @Accessor("format")
    VertexFormat sleep$shFormat();

    @Accessor("building")
    boolean sleep$shBuilding();
}
