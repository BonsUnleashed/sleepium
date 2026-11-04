package dev.lull.opt;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/shader-samplers (client, with ShaderSamplerCacheMixin's flag): setDefaultUniforms without building "Sampler" + i
 * for 12 samplers on every draw. An overwrite: it gives way where another mod changes the method (MixinYield).
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderSamplerNamesMixin {
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform MODEL_VIEW_MATRIX;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform PROJECTION_MATRIX;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform COLOR_MODULATOR;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform GLINT_ALPHA;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform FOG_START;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform FOG_END;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform FOG_COLOR;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform FOG_SHAPE;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform TEXTURE_MATRIX;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform GAME_TIME;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform SCREEN_SIZE;
    @Shadow @org.jetbrains.annotations.Nullable @Final public com.mojang.blaze3d.shaders.Uniform LINE_WIDTH;
    @Shadow public abstract void setSampler(String name, Object textureId);

    @org.spongepowered.asm.mixin.Unique
    private static final String[] PROBE$SAMPLERS = {"Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4", "Sampler5", "Sampler6",
            "Sampler7", "Sampler8", "Sampler9", "Sampler10", "Sampler11"};

    /**
     * @author probe (lab.opt.ShaderSamplerCacheMixin)
     * @reason vanilla builds "Sampler" + i for 12 samplers on every draw (13 % of the render thread's allocation in an
     *         entity-heavy scene); the same names, made once. The rest is vanilla's body.
     */
    @org.spongepowered.asm.mixin.Overwrite
    public void setDefaultUniforms(com.mojang.blaze3d.vertex.VertexFormat.Mode mode, org.joml.Matrix4f frustumMatrix, org.joml.Matrix4f projectionMatrix,
            com.mojang.blaze3d.platform.Window window) {
        for (int i = 0; i < 12; i++) {
            int j = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(i);
            this.setSampler(PROBE$SAMPLERS[i], j);
        }
        if (this.MODEL_VIEW_MATRIX != null) this.MODEL_VIEW_MATRIX.set(frustumMatrix);
        if (this.PROJECTION_MATRIX != null) this.PROJECTION_MATRIX.set(projectionMatrix);
        if (this.COLOR_MODULATOR != null) this.COLOR_MODULATOR.set(com.mojang.blaze3d.systems.RenderSystem.getShaderColor());
        if (this.GLINT_ALPHA != null) this.GLINT_ALPHA.set(com.mojang.blaze3d.systems.RenderSystem.getShaderGlintAlpha());
        if (this.FOG_START != null) this.FOG_START.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart());
        if (this.FOG_END != null) this.FOG_END.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
        if (this.FOG_COLOR != null) this.FOG_COLOR.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor());
        if (this.FOG_SHAPE != null) this.FOG_SHAPE.set(com.mojang.blaze3d.systems.RenderSystem.getShaderFogShape().getIndex());
        if (this.TEXTURE_MATRIX != null) this.TEXTURE_MATRIX.set(com.mojang.blaze3d.systems.RenderSystem.getTextureMatrix());
        if (this.GAME_TIME != null) this.GAME_TIME.set(com.mojang.blaze3d.systems.RenderSystem.getShaderGameTime());
        if (this.SCREEN_SIZE != null) this.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        if (this.LINE_WIDTH != null && (mode == com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES || mode == com.mojang.blaze3d.vertex.VertexFormat.Mode.LINE_STRIP))
            this.LINE_WIDTH.set(com.mojang.blaze3d.systems.RenderSystem.getShaderLineWidth());
        com.mojang.blaze3d.systems.RenderSystem.setupShaderLights((ShaderInstance) (Object) this);
    }

}
