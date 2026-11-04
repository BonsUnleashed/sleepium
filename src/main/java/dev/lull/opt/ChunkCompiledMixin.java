package dev.lull.opt;

import dev.lull.optsupport.ChunkMultiDraw;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ChunkMultiDraw on 1.20.1 (1.21.1: ChunkSectionMixin's hook on RenderSection.setCompiled): RebuildTask publishes a
 * compile with compiled.set inside a lambda (no method of its own to hook by name in production), then calls
 * addRecentlyCompiledChunk on the same thread - the new compile is the chunk's current one there. Its own mixin
 * (not ChunkMultiDrawMixin's): it must stay when multi-draw's renderChunkLayer mixin gives way to another mod.
 */
@Mixin(LevelRenderer.class)
public abstract class ChunkCompiledMixin {
    @Inject(method = "addRecentlyCompiledChunk", at = @At("HEAD"))
    private void sleep$compiled(ChunkRenderDispatcher.RenderChunk chunk, CallbackInfo ci) {
        ChunkMultiDraw.compiled(chunk, chunk.getCompiledChunk());
    }
}
