package dev.sleep.mixin;

import dev.sleep.optsupport.BrainTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.brainTime=true): BrainTime around a sensor's tick. */
@Mixin(Sensor.class)
public abstract class BrainTimeSensorMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void probe$in(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        BrainTime.enter(this, "sensor ");
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void probe$out(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        BrainTime.exit();
    }
}
