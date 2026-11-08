package dev.lull.opt;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ItemGpu: a sprite's pixels (which texels are fully opaque or discarded). */
@Mixin(SpriteContents.class)
public interface SpriteImageAccessor {
    @Accessor("originalImage")
    NativeImage sleep$image();
}
