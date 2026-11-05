package dev.lull.opt;

import dev.lull.optsupport.PartBatch;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PartBatchMixin: a render type drawn by something other than RenderType.draw - the batching buffer sources of other
 * mods (Oculus / Iris' FullyBufferedMultiBufferSource draws its segments between setupRenderState and
 * clearRenderState) - is the current type too, so its queued parts / residents are drawn right after it as after
 * vanilla's own end.
 */
@Mixin(RenderStateShard.class)
public abstract class PartShardMixin {
    @Inject(method = "setupRenderState", at = @At("HEAD"))
    private void sleep$begin(CallbackInfo ci) {
        if ((Object) this instanceof RenderType t) PartBatch.beginShard(t);
    }

    @Inject(method = "clearRenderState", at = @At("TAIL"))
    private void sleep$end(CallbackInfo ci) {
        if ((Object) this instanceof RenderType) PartBatch.endShard();
    }
}
