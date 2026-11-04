package dev.lull.catmixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** KineticResident: the render section of a global block entity (not in the section loop). 1.20.1: a RenderChunk. */
@Mixin(ViewArea.class)
public interface ViewAreaInvoker {
    @Invoker("getRenderChunkAt")
    ChunkRenderDispatcher.RenderChunk sleep$sectionAt(BlockPos pos);
}
