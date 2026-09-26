package dev.probe.mixin;

import dev.probe.optsupport.sleep.EntitySleep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Entity sleep: the entity's tick() in the level's entity loop goes through the engine (lab.opt.EntitySleep / diag). */
@Mixin(ServerLevel.class)
public abstract class EntSleepTickMixin {
    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void probe$tick(Entity e) {
        EntitySleep.tick(e);
    }
}
