package dev.lull.mixin;

import dev.lull.optsupport.MoveTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsleep.moveTime=true): MoveTime's LivingEntity parts. */
@Mixin(LivingEntity.class)
public abstract class MoveTimeLivingMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$tickIn(CallbackInfo ci) { MoveTime.enter(MoveTime.TICK, (Entity) (Object) this); }

    @Inject(method = "tick", at = @At("RETURN"))
    private void sleep$tickOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void sleep$aiStepIn(CallbackInfo ci) { MoveTime.enter(MoveTime.AISTEP, (Entity) (Object) this); }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void sleep$aiStepOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "travel", at = @At("HEAD"))
    private void sleep$travelIn(Vec3 v, CallbackInfo ci) { MoveTime.enter(MoveTime.TRAVEL, (Entity) (Object) this); }

    @Inject(method = "travel", at = @At("RETURN"))
    private void sleep$travelOut(Vec3 v, CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "pushEntities", at = @At("HEAD"))
    private void sleep$pushIn(CallbackInfo ci) { MoveTime.enter(MoveTime.PUSH, (Entity) (Object) this); }

    @Inject(method = "pushEntities", at = @At("RETURN"))
    private void sleep$pushOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void sleep$aiIn(CallbackInfo ci) { MoveTime.enter(MoveTime.AI, (Entity) (Object) this); }

    @Inject(method = "serverAiStep", at = @At("RETURN"))
    private void sleep$aiOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }
}
