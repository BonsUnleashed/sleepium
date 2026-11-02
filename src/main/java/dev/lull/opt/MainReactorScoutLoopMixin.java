package dev.lull.opt;

import dev.lull.optsupport.MainReactor;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement (lab.reactor.scout): a queued task ran right after a timeout wake — its producer did not signal. */
@Mixin(BlockableEventLoop.class)
public abstract class MainReactorScoutLoopMixin {
    @Inject(method = "doRunTask", at = @At("HEAD"))
    private void sleep$task(Runnable task, CallbackInfo ci) {
        MainReactor.found("queued task: " + ((BlockableEventLoop<?>) (Object) this).name());
    }
}
