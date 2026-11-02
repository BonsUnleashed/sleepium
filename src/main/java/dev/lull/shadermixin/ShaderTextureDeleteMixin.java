package dev.lull.shadermixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A deleted texture's cached size (atlasSize) is forgotten: its id may be handed out again. */
@Mixin(GlStateManager.class)
public class ShaderTextureDeleteMixin {
    @ModifyVariable(method = "_deleteTexture", at = @At("HEAD"), argsOnly = true, remap = false)
    private static int sleep$forgetSize(int texture) {
        dev.lull.shaders.PackUniforms.forgetTexture(texture);
        return texture;
    }
}
