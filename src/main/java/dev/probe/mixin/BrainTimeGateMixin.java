package dev.probe.mixin;

import dev.probe.optsupport.BrainTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dprobe.brainTime=true): BrainTime around a gate behaviour's (its children's time subtracted) tryStart / tickOrStop. */
@Mixin(GateBehavior.class)
public abstract class BrainTimeGateMixin {
    @Inject(method = "tryStart", at = @At("HEAD"))
    private void probe$startIn(ServerLevel level, LivingEntity owner, long time, CallbackInfoReturnable<Boolean> cir) {
        BrainTime.enter(this, "gate ");
    }

    @Inject(method = "tryStart", at = @At("RETURN"))
    private void probe$startOut(ServerLevel level, LivingEntity owner, long time, CallbackInfoReturnable<Boolean> cir) {
        BrainTime.exit();
    }

    @Inject(method = "tickOrStop", at = @At("HEAD"))
    private void probe$tickIn(ServerLevel level, LivingEntity owner, long time, CallbackInfo ci) {
        BrainTime.enter(this, "gate ");
    }

    @Inject(method = "tickOrStop", at = @At("RETURN"))
    private void probe$tickOut(ServerLevel level, LivingEntity owner, long time, CallbackInfo ci) {
        BrainTime.exit();
    }
}
