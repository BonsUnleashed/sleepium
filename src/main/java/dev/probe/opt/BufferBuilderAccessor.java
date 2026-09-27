package dev.probe.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RenderCacheMixins: appending complete recorded vertices to a BufferBuilder in one copy. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("buffer")
    ByteBufferBuilder probe$buffer();

    @Accessor("vertices")
    int probe$vertices();

    @Accessor("vertices")
    void probe$setVertices(int v);

    @Accessor("vertexPointer")
    void probe$setVertexPointer(long p);

    @Accessor("elementsToFill")
    int probe$elementsToFill();

    @Accessor("format")
    VertexFormat probe$format();

    @Accessor("mode")
    VertexFormat.Mode probe$mode();

    @Accessor("building")
    boolean probe$building();
}
