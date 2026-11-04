package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.lull.optsupport.LightVolumes;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ShaderLightMixin: a section compile with recipes (dynamic sections), the sentinel pass before each layer is ended.
 * 1.20.1: the compile is RenderChunk.RebuildTask.compile (1.21.1: SectionCompiler.compile), its chunk the task's outer
 * instance; a layer's builder comes from ChunkBufferBuilderPack.builder (1.21.1: getOrBeginLayer), a layer ends with
 * endOrDiscardIfEmpty (1.21.1: build).
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class ShaderLightCompileMixin {
    @Shadow @Final ChunkRenderDispatcher.RenderChunk this$1;

    @Inject(method = "compile", at = @At("HEAD"))
    private void sleep$begin(float x, float y, float z, ChunkBufferBuilderPack pack, CallbackInfoReturnable<?> cir) {
        LightVolumes.beginCompile(SectionPos.of(this$1.getOrigin()));
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void sleep$end(float x, float y, float z, ChunkBufferBuilderPack pack, CallbackInfoReturnable<?> cir) {
        LightVolumes.endCompile();
    }

    /** every layer the section uses: one not drawn from our arenas makes the section incomplete (it keeps baked light) */
    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ChunkBufferBuilderPack;builder(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"))
    private BufferBuilder sleep$layer(ChunkBufferBuilderPack pack, RenderType type) {
        LightVolumes.layerUsed(type);
        return pack.builder(type);
    }

    @Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;endOrDiscardIfEmpty()Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;"))
    private BufferBuilder.RenderedBuffer sleep$build(BufferBuilder bb) {
        boolean recipes = LightVolumes.beforeBuild(bb);
        BufferBuilder.RenderedBuffer md = bb.endOrDiscardIfEmpty();
        if (recipes && md != null) ((LightVolumes.RecipeMesh) md).sleep$setRecipeInfo(LightVolumes.currentInfo());
        return md;
    }
}
