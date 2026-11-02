package dev.lull.mixin;

import dev.lull.optsupport.BrainTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsleep.brainTime=true): the whole Brain.tick, BrainTime's reference. */
@Mixin(Brain.class)
public abstract class BrainTimeTickMixin {
    @Unique private static long sleep$t0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$in(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        sleep$t0 = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void sleep$out(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        BrainTime.brain(System.nanoTime() - sleep$t0, level.getGameTime());
    }
}
