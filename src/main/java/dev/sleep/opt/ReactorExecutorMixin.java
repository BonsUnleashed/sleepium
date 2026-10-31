package dev.sleep.opt;

import dev.sleep.optsupport.FullRing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldgenReactor: the level's main-thread executor also serves FullRing — within the idle budget when called from
 * the idle loop, without a budget when the main thread blocks on a chunk future (managedBlock).
 */
@Mixin(targets = "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor")
public abstract class ReactorExecutorMixin {
    @Inject(method = "pollTask", at = @At("HEAD"), cancellable = true)
    private void probe$ring(CallbackInfoReturnable<Boolean> cir) {
        if (FullRing.runOne()) cir.setReturnValue(true);
    }
}
