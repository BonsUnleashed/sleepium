package dev.lull.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.lull.DrawStats;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * -Dsleep.drawStats: every immediate-mode batch drawn by a render type. 1.20.1: RenderType.end hands the ended buffer to
 * BufferUploader.drawWithShader (1.21.1: RenderType.draw(MeshData)); it is counted there.
 */
@Mixin(RenderType.class)
public abstract class DrawStatsMixin {
    @ModifyArg(method = "end", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V"))
    private BufferBuilder.RenderedBuffer sleep$draw(BufferBuilder.RenderedBuffer mesh) {
        DrawStats.draw(this.toString(), mesh.drawState().vertexCount(), mesh.drawState().vertexBufferSize());
        return mesh;
    }
}
