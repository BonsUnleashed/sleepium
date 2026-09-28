package dev.probe.opt;

import dev.probe.optsupport.MainReactor;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measurement (lab.reactor.scout): after the reactor woke by its timeout (no signal), which main-thread work was
 * waiting — found only because the thread polled.
 */
@Mixin(ServerChunkCache.class)
public abstract class MainReactorScoutMixin {
    @Inject(method = "runDistanceManagerUpdates", at = @At("RETURN"))
    private void probe$dm(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) MainReactor.found("distance manager / generation tasks");
    }
}
