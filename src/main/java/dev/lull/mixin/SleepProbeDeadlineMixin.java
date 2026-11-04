package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sleep engine: sleepers whose deadline has come are woken from the time queue before the block entities tick. */
@Mixin(Level.class)
public abstract class SleepProbeDeadlineMixin {
    @Inject(method = "tickBlockEntities", at = @At("HEAD"))
    private void sleep$deadlines(CallbackInfo ci) {
        SleepEngine.pollDeadlines((Level) (Object) this);
    }
}
