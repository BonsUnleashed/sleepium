package dev.lull.mixin;

import dev.lull.optsupport.TextureMips;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** TextureMips: a marked texture keeps its mip filter whatever filter a render type asks for when it binds it */
@Mixin(AbstractTexture.class)
public abstract class TextureMipsFlagMixin implements TextureMips.Flag {
    @Unique private boolean sleep$mips;

    @Override
    public boolean sleep$mips() {
        return sleep$mips;
    }

    @Override
    public void sleep$setMips(boolean on) {
        sleep$mips = on;
    }

    @ModifyVariable(method = "setFilter", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private boolean sleep$mipFilter(boolean mipmap) {
        return mipmap || sleep$mips;
    }
}
