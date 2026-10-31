package dev.sleep.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sleep.optsupport.TextureMips;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TextureMips: a registered texture's mip levels built once its level 0 is uploaded (on the render thread) */
@Mixin(SimpleTexture.class)
public abstract class TextureMipsLoadMixin {
    @Shadow @Final protected ResourceLocation location;

    @Inject(method = "doLoad", at = @At("HEAD"))
    private void sleep$bleed(NativeImage image, boolean blur, boolean clamp, CallbackInfo ci) {
        if (TextureMips.wants(location)) TextureMips.bleed(image);
    }

    @Inject(method = "doLoad", at = @At("RETURN"))
    private void sleep$mips(NativeImage image, boolean blur, boolean clamp, CallbackInfo ci) {
        if (!TextureMips.wants(location)) return;
        TextureMips.build(((SimpleTexture) (Object) this).getId());
        ((TextureMips.Flag) this).sleep$setMips(true);
    }
}
