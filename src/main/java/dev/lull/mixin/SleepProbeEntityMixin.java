package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every entity box change (spawn, move, load, a new size) may move it into or out of a box a sleeper read: one event
 * over the old and the new box together. (The position is not the hook: vanilla sets the box after it, so a test there
 * saw the old box - a boat stopping in a sleeping item's box never woke it, tools/exactness.py vehicles.) Sleep engines
 * only.
 */
@Mixin(Entity.class)
public abstract class SleepProbeEntityMixin {
    @Inject(method = "setBoundingBox", at = @At("HEAD"))
    private void sleep$boxed(AABB bb, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        SleepEngine.onMove(self, self.getBoundingBox(), bb);
    }
}
