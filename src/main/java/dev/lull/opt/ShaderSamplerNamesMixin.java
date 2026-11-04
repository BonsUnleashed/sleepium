package dev.lull.opt;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/shader-samplers (client, with ShaderSamplerCacheMixin's flag): the immediate draws' shader setup without building
 * "Sampler" + i for 12 samplers on every draw. 1.20.1 has no ShaderInstance.setDefaultUniforms: the same setup is
 * VertexBuffer._drawWithShader's head (BufferUploader.drawWithShader of every immediate batch goes through it; the
 * chunk layers' copy in renderChunkLayer is ChunkMultiDrawMixin's). An overwrite: it gives way where another mod
 * changes the method (MixinYield).
 */
@Mixin(VertexBuffer.class)
public abstract class ShaderSamplerNamesMixin {
    @Shadow private VertexFormat.Mode mode;

    @Shadow
    public abstract void draw();

    @org.spongepowered.asm.mixin.Unique
    private static final String[] PROBE$SAMPLERS = {"Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4", "Sampler5", "Sampler6",
            "Sampler7", "Sampler8", "Sampler9", "Sampler10", "Sampler11"};

    /**
     * @author probe (lab.opt.ShaderSamplerCacheMixin)
     * @reason vanilla builds "Sampler" + i for 12 samplers on every draw (13 % of the render thread's allocation in an
     *         entity-heavy scene, 1.21.1); the same names, made once. The rest is vanilla's body.
     */
    @org.spongepowered.asm.mixin.Overwrite
    private void _drawWithShader(org.joml.Matrix4f modelViewMatrix, org.joml.Matrix4f projectionMatrix, ShaderInstance shader) {
        for (int i = 0; i < 12; ++i) {
            int j = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(i);
            shader.setSampler(PROBE$SAMPLERS[i], j);
        }
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(projectionMatrix);
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) shader.INVERSE_VIEW_ROTATION_MATRIX.set(com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix());
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(com.mojang.blaze3d.systems.RenderSystem.getShaderColor());
        if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(com.mojang.blaze3d.systems.RenderSystem.getShaderGlintAlpha());
        if (shader.FOG_START != null) shader.FOG_START.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(com.mojang.blaze3d.systems.RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(com.mojang.blaze3d.systems.RenderSystem.getShaderGameTime());
        if (shader.SCREEN_SIZE != null) {
            com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        if (shader.LINE_WIDTH != null && (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP))
            shader.LINE_WIDTH.set(com.mojang.blaze3d.systems.RenderSystem.getShaderLineWidth());
        com.mojang.blaze3d.systems.RenderSystem.setupShaderLights(shader);
        shader.apply();
        this.draw();
        shader.clear();
    }
}
