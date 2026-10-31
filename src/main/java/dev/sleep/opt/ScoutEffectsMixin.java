package dev.sleep.opt;

import dev.sleep.optsupport.ParallelTickScout;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/parallel-tick-scout (measurement only): the level-wide side effects a task can have (entity lists, the game
 * event dispatcher, the block event queue, packets to players). Handlers take no arguments: only the call matters.
 */
@Mixin(ServerLevel.class)
public abstract class ScoutEffectsMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void probe$add(CallbackInfoReturnable<Boolean> cir) {
        ParallelTickScout.effect(ParallelTickScout.ENTITY_ADD_REMOVE);
    }

    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void probe$gameEvent(CallbackInfo ci) {
        ParallelTickScout.effect(ParallelTickScout.GAME_EVENT);
    }

    @Inject(method = "blockEvent", at = @At("HEAD"))
    private void probe$blockEvent(CallbackInfo ci) {
        ParallelTickScout.effect(ParallelTickScout.BLOCK_EVENT_Q);
    }

    @Inject(method = {"playSeededSound", "levelEvent", "broadcastEntityEvent"}, at = @At("HEAD"))
    private void probe$packets(CallbackInfo ci) {
        ParallelTickScout.effect(ParallelTickScout.PACKETS);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void probe$particles(CallbackInfoReturnable<Integer> cir) {
        ParallelTickScout.effect(ParallelTickScout.PACKETS);
    }
}
