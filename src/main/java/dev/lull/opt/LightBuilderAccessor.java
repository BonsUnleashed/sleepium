package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * exp/shader-light on 1.20.1 (LightVolumes): the builder's vertices as the recipes need them - its buffer, the write
 * offset after the last vertex, the current batch's vertex count, its format. (1.21.1 read them through the render
 * cache's BufferBuilderAccessor; names of its own here, so the two accessors can apply together.)
 */
@Mixin(BufferBuilder.class)
public interface LightBuilderAccessor {
    @Accessor("buffer")
    ByteBuffer sleep$lvBuffer();

    @Accessor("nextElementByte")
    int sleep$lvNext();

    @Accessor("vertices")
    int sleep$lvVertices();

    @Accessor("format")
    VertexFormat sleep$lvFormat();
}
