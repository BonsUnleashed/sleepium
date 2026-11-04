package dev.lull.opt;

import dev.lull.optsupport.PartBatch;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PartBatchMixin: no queued part outlives the level render. */
@Mixin(LevelRenderer.class)
public abstract class PartFrameMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void sleep$end(CallbackInfo ci) {
        PartBatch.endFrame();
    }
}
