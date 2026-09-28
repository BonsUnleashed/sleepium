package dev.probe.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.probe.optsupport.LightVolumes;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ShaderLightMixin: a section compile with recipes (dynamic sections), the sentinel pass before each layer is built. */
@Mixin(SectionCompiler.class)
public abstract class ShaderLightCompileMixin {
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Inject(method = COMPILE, at = @At("HEAD"))
    private void probe$begin(SectionPos section, net.minecraft.client.renderer.chunk.RenderChunkRegion region, com.mojang.blaze3d.vertex.VertexSorting sorting,
                             net.minecraft.client.renderer.SectionBufferBuilderPack pack, java.util.List<?> extra, CallbackInfoReturnable<?> cir) {
        LightVolumes.beginCompile(section);
    }

    @Inject(method = COMPILE, at = @At("RETURN"))
    private void probe$end(CallbackInfoReturnable<?> cir) {
        LightVolumes.endCompile();
    }

    @Redirect(method = COMPILE, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;build()Lcom/mojang/blaze3d/vertex/MeshData;"))
    private MeshData probe$build(BufferBuilder bb) {
        boolean recipes = LightVolumes.beforeBuild(bb);
        MeshData md = bb.build();
        if (recipes && md != null) ((LightVolumes.RecipeMesh) md).probe$setRecipes(true);
        return md;
    }
}
