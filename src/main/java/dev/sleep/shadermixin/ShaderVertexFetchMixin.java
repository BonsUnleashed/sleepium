package dev.sleep.shadermixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.sleep.shaders.ShaderPipeline;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * While a shaderpack renders the level, the vertex buffer being drawn is also bound as a storage buffer (binding 9):
 * the pack programs' vertex prologue reads the other vertices of its quad (mc_midTexCoord, at_tangent, at_midBlock).
 */
@Mixin(VertexBuffer.class)
public class ShaderVertexFetchMixin {
    @Shadow
    private int vertexBufferId;

    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;drawElements(III)V"), index = 0)
    private int sleep$fetchBuffer(int mode) {
        if (ShaderPipeline.active) {
            ShaderPipeline p = ShaderPipeline.current();
            // only for a program that reads it: a storage binding changed per draw makes the driver validate again
            if (p.inLevel && p.fetch) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 9, vertexBufferId);
        }
        return mode;
    }
}
