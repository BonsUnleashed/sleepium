package dev.probe.mixin;

import dev.probe.Knock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.knock=...): Brain parts switched off (Knock). */
@Mixin(Brain.class)
public abstract class KnockMixin {
    @Inject(method = "tickSensors", at = @At("HEAD"), cancellable = true)
    private void probe$sensors(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.SENSORS) ci.cancel();
    }

    @Inject(method = "startEachNonRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void probe$start(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.START) ci.cancel();
    }

    @Inject(method = "tickEachRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void probe$run(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (Knock.RUN) ci.cancel();
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"), cancellable = true)
    private void probe$forget(CallbackInfo ci) {
        if (Knock.FORGET) ci.cancel();
    }
}
