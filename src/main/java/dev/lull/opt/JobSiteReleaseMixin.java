package dev.lull.opt;

import dev.lull.optsupport.JobSiteBrainAccess;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.JobSiteIndex: a removed entity's brain gives back its job site (a removed villager is never a competitor). */
@Mixin(Entity.class)
public abstract class JobSiteReleaseMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void sleep$removed(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof LivingEntity e && !e.level().isClientSide) ((JobSiteBrainAccess) e.getBrain()).sleep$release();
    }
}
