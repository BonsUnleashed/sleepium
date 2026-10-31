package dev.sleep.opt;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.sleep.optsupport.LightVolumes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** ShaderLightMixin: vanilla's per-section draws of the translucent layer read the section's light volume. */
@Mixin(LevelRenderer.class)
public abstract class ShaderLightTranslucentMixin {
    @Redirect(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void probe$draw(VertexBuffer vb, @Local(argsOnly = true) RenderType type, @Local SectionRenderDispatcher.RenderSection section) {
        boolean translucent = type == RenderType.translucent();
        if (translucent) LightVolumes.translucentDraw(section);
        vb.draw();
        if (translucent) LightVolumes.translucentDrawn();
    }
}
