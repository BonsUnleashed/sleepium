package dev.probe.mixin;

import dev.probe.Knock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.knock=...): LivingEntity movement and pushing switched off on the server (Knock). */
@Mixin(LivingEntity.class)
public abstract class KnockEntityMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void probe$travel(Vec3 v, CallbackInfo ci) {
        if (Knock.TRAVEL && !((LivingEntity) (Object) this).level().isClientSide) ci.cancel();
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void probe$push(CallbackInfo ci) {
        if (Knock.PUSH && !((LivingEntity) (Object) this).level().isClientSide) ci.cancel();
    }
}
