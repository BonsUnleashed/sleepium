package dev.lull.optsupport;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;

/** On VertexBuffer (ChunkUploadMixin): the render chunk and chunk layer a section buffer belongs to (null otherwise). */
public interface ChunkBufferTag {
    void sleep$tag(ChunkRenderDispatcher.RenderChunk section, RenderType layer);
}
