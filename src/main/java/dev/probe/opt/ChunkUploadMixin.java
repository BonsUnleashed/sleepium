package dev.probe.opt;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.probe.optsupport.ChunkBufferTag;
import dev.probe.optsupport.ChunkMultiDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ChunkMultiDraw: a section layer's mesh is also copied into its region arena before vanilla uploads it. */
@Mixin(VertexBuffer.class)
public abstract class ChunkUploadMixin implements ChunkBufferTag {
    @Unique private SectionRenderDispatcher.RenderSection probe$section;
    @Unique private RenderType probe$layer;

    @Override
    public void probe$tag(SectionRenderDispatcher.RenderSection section, RenderType layer) {
        probe$section = section;
        probe$layer = layer;
    }

    @org.spongepowered.asm.mixin.Shadow private com.mojang.blaze3d.vertex.VertexFormat format;

    /** exp/shader-light: a section's vanilla BLOCK buffer (the translucent layer) exposes the recipe bytes (attribute 7) */
    @Inject(method = "upload(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("RETURN"))
    private void probe$recipeAttribute(MeshData mesh, CallbackInfo ci) {
        if (probe$section != null && dev.probe.optsupport.LightVolumes.translucentPatched && format == com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK)
            dev.probe.optsupport.LightVolumes.vanillaBufferUploaded();
    }

    @Inject(method = "upload(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"), cancellable = true)
    private void probe$store(MeshData mesh, CallbackInfo ci) {
        if (probe$section == null) return;
        boolean ours = ChunkMultiDraw.store(probe$section, probe$layer, mesh);
        if (dev.probe.optsupport.LightVolumes.ON) dev.probe.optsupport.LightVolumes.uploaded(probe$section, probe$layer, mesh, ours);
        if (ours && ChunkMultiDraw.replacesVanilla(probe$layer)) {
            mesh.close();   // what vanilla's upload does at the end
            ci.cancel();
        }
    }
}
