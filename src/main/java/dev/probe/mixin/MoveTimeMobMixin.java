package dev.probe.mixin;

import dev.probe.optsupport.MoveTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.moveTime=true): MoveTime's AI part for Mob (its own final serverAiStep). */
@Mixin(Mob.class)
public abstract class MoveTimeMobMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void probe$aiIn(CallbackInfo ci) { MoveTime.enter(MoveTime.AI, (Entity) (Object) this); }

    @Inject(method = "serverAiStep", at = @At("RETURN"))
    private void probe$aiOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }
}
