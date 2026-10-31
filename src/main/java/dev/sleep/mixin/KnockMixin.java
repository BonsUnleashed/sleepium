package dev.sleep.mixin;

import dev.sleep.Knock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsleep.knock=...): Brain parts switched off (Knock). */
@Mixin(Brain.class)
public abstract class KnockMixin {
    @Inject(method = "tickSensors", at = @At("HEAD"), cancellable = true)
    private void sleep$sensors(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.SENSORS) ci.cancel();
    }

    @Inject(method = "startEachNonRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void sleep$start(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.START) ci.cancel();
    }

    @Inject(method = "tickEachRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void sleep$run(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.RUN) ci.cancel();
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"), cancellable = true)
    private void sleep$forget(CallbackInfo ci) {
        if (Knock.FORGET) ci.cancel();
    }
}
