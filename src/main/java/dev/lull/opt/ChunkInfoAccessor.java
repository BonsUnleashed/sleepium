package dev.lull.opt;

import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * ChunkMultiDraw / SectionLists on 1.20.1: LevelRenderer's visible list (renderChunksInFrustum) holds RenderChunkInfo,
 * a package-private class (1.21.1 listed the RenderSections themselves). Its chunk and its resort test, without an
 * access transformer.
 */
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer$RenderChunkInfo")
public interface ChunkInfoAccessor {
    @Accessor("chunk")
    ChunkRenderDispatcher.RenderChunk sleep$chunk();

    @Invoker("isAxisAlignedWith")
    boolean sleep$axisAligned(int x, int y, int z);
}
