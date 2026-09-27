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

    @Inject(method = "upload(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"), cancellable = true)
    private void probe$store(MeshData mesh, CallbackInfo ci) {
        if (probe$section != null && ChunkMultiDraw.store(probe$section, probe$layer, mesh) && ChunkMultiDraw.replacesVanilla(probe$layer)) {
            mesh.close();   // what vanilla's upload does at the end
            ci.cancel();
        }
    }
}
