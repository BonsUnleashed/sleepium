package dev.lull.opt;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.lull.optsupport.QuadSort;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** VertexSortRadixMixin: the translucent sort of BufferSource taken straight from the vertex buffer (QuadSort.sortQuads). */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class VertexSortDirectMixin {
    @Redirect(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/MeshData;sortQuads(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)Lcom/mojang/blaze3d/vertex/MeshData$SortState;"))
    private MeshData.SortState sleep$sort(MeshData m, ByteBufferBuilder bb, VertexSorting sorting) {
        return QuadSort.sortQuads(m, bb, sorting);
    }
}
