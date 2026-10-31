package dev.sleep.opt;

import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measurement only (-Dlab.lighttime=true): time spent in LevelLightEngine.runLightUpdates per thread group (the
 * client's engine on the render thread / light worker, the server's on its light mailbox), printed every 2000 calls.
 */
@Mixin(LevelLightEngine.class)
public abstract class LightTimingMixin {
    @Unique private static final ThreadLocal<long[]> sleep$start = ThreadLocal.withInitial(() -> new long[1]);

    @Inject(method = "runLightUpdates", at = @At("HEAD"))
    private void sleep$start(CallbackInfoReturnable<Integer> cir) {
        sleep$start.get()[0] = System.nanoTime();
    }

    @Inject(method = "runLightUpdates", at = @At("RETURN"))
    private void sleep$end(CallbackInfoReturnable<Integer> cir) {
        dev.sleep.optsupport.LightTiming.add(System.nanoTime() - sleep$start.get()[0]);
    }
}
