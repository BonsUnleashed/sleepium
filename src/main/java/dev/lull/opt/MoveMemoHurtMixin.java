package dev.lull.opt;

import dev.lull.optsupport.sleep.MoveMemo;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lab.opt.MoveMemo: the damage path (LivingEntity.hurt - isDamageSourceBlocked reads the head yaw - and
 * setLastHurtByMob, which reads tickCount) marks its runs (MoveMemo.INSTRUMENTED).
 */
@Mixin(LivingEntity.class)
public abstract class MoveMemoHurtMixin {
    @Inject(method = "hurt", at = @At("HEAD"))
    private void sleep$hurtIn(DamageSource src, float amount, CallbackInfoReturnable<Boolean> cir) {
        MoveMemo.readerRan((LivingEntity) (Object) this);
    }

    @Inject(method = "setLastHurtByMob", at = @At("HEAD"))
    private void sleep$hurt(LivingEntity by, CallbackInfo ci) {
        MoveMemo.readerRan((LivingEntity) (Object) this);
    }
}
