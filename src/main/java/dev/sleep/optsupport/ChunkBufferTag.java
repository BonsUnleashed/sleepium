package dev.sleep.optsupport;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/** On VertexBuffer (ChunkUploadMixin): the render section and chunk layer a section buffer belongs to (null otherwise). */
public interface ChunkBufferTag {
    void probe$tag(SectionRenderDispatcher.RenderSection section, RenderType layer);
}
