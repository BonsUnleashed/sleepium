package dev.sleep.mixin;

import dev.sleep.optsupport.BrainTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.brainTime=true): the whole Brain.tick, BrainTime's reference. */
@Mixin(Brain.class)
public abstract class BrainTimeTickMixin {
    @Unique private static long probe$t0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void probe$in(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        probe$t0 = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void probe$out(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        BrainTime.brain(System.nanoTime() - probe$t0, level.getGameTime());
    }
}
