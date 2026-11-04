package dev.lull.opt;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** VertexSortDirectMixin: the sorted index buffer a direct sort builds. */
@Mixin(MeshData.class)
public interface MeshDataAccessor {
    @Accessor("indexBuffer")
    void sleep$setIndexBuffer(ByteBufferBuilder.Result r);
}
