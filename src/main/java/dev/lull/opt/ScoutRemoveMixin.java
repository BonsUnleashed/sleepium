package dev.lull.opt;

import dev.lull.optsupport.ParallelTickScout;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** exp/parallel-tick-scout (measurement only): removing an entity changes the level's entity lists. */
@Mixin(Entity.class)
public abstract class ScoutRemoveMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void sleep$remove(Entity.RemovalReason reason, CallbackInfo ci) {
        ParallelTickScout.effect(ParallelTickScout.ENTITY_ADD_REMOVE);
    }
}
