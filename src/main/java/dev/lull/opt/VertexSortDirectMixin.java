package dev.lull.opt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.lull.optsupport.QuadSort;
import java.nio.ByteBuffer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VertexSortRadixMixin: the translucent sort of world rendering taken straight from the vertex buffer
 * (QuadSort.putDirect). 1.21.1 redirected MeshData.sortQuads in BufferSource.endBatch; 1.20.1 sorts inside
 * BufferBuilder: setQuadSorting makes a Vector3f per quad at once (makeQuadSortingPoints), storeRenderedBuffer writes the
 * indices (putSortedQuadIndices). For DISTANCE_TO_ORIGIN on the render thread (RenderType.end of the level's buffers)
 * the points are not made - a marker stands for them - and the indices come from the vertices themselves. Anything that
 * reads the points (getSortState: chunk compiles keep them for resorts) gets vanilla's points, made then.
 */
@Mixin(BufferBuilder.class)
public abstract class VertexSortDirectMixin {
    @Shadow private ByteBuffer buffer;
    @Shadow private int renderedBufferPointer;
    @Shadow private int nextElementByte;
    @Shadow private int vertices;
    @Shadow private VertexFormat format;
    @Shadow private Vector3f[] sortingPoints;
    @Shadow private VertexSorting sorting;
    @Shadow private boolean indexOnly;
    /** the vertex count when the marker was set (vanilla's points cover the quads written by then) */
    @Unique private int sleep$sortVertices;

    @Shadow
    private Vector3f[] makeQuadSortingPoints() {
        return null;
    }

    @Shadow
    private void putSortedQuadIndices(VertexFormat.IndexType indexType) {}

    @Redirect(method = "setQuadSorting", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;makeQuadSortingPoints()[Lorg/joml/Vector3f;"))
    private Vector3f[] sleep$points(BufferBuilder self) {
        if (this.sorting == VertexSorting.DISTANCE_TO_ORIGIN && !this.indexOnly && RenderSystem.isOnRenderThread()) {
            sleep$sortVertices = this.vertices;
            return QuadSort.DIRECT;
        }
        return makeQuadSortingPoints();
    }

    @Redirect(method = "storeRenderedBuffer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;putSortedQuadIndices(Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V"))
    private void sleep$indices(BufferBuilder self, VertexFormat.IndexType type) {
        if (this.sortingPoints == QuadSort.DIRECT) {
            if (this.sorting == VertexSorting.DISTANCE_TO_ORIGIN && sleep$sortVertices == this.vertices) {
                QuadSort.putDirect(this.buffer, this.renderedBufferPointer, this.nextElementByte, this.vertices, this.format, type);
                return;
            }
            this.sortingPoints = sleep$vanillaPoints();   // the sorting or the vertices changed since: vanilla's points after all
        }
        putSortedQuadIndices(type);
    }

    @Inject(method = "getSortState", at = @At("HEAD"))
    private void sleep$state(CallbackInfoReturnable<BufferBuilder.SortState> cir) {
        if (this.sortingPoints == QuadSort.DIRECT) this.sortingPoints = sleep$vanillaPoints();
    }

    /** the points setQuadSorting would have made: over the vertices written when it was called */
    @Unique
    private Vector3f[] sleep$vanillaPoints() {
        int now = this.vertices;
        this.vertices = sleep$sortVertices;
        try {
            return makeQuadSortingPoints();
        } finally {
            this.vertices = now;
        }
    }
}
