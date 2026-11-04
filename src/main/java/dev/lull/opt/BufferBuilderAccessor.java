package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RenderCacheMixins: appending complete recorded vertices to a BufferBuilder in one copy. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("buffer")
    ByteBufferBuilder sleep$buffer();

    @Accessor("vertices")
    int sleep$vertices();

    @Accessor("vertices")
    void sleep$setVertices(int v);

    @Accessor("vertexPointer")
    void sleep$setVertexPointer(long p);

    @Accessor("vertexPointer")
    long sleep$vertexPointer();

    @Accessor("elementsToFill")
    int sleep$elementsToFill();

    @Accessor("format")
    VertexFormat sleep$format();

    @Accessor("mode")
    VertexFormat.Mode sleep$mode();

    @Accessor("building")
    boolean sleep$building();
}
