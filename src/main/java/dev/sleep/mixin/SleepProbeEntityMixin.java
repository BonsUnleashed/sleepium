package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.SleepEngine;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every entity position update (spawn, move, load) may enter a box a sleeping block entity read. Sleep engine only. */
@Mixin(Entity.class)
public abstract class SleepProbeEntityMixin {
    @Inject(method = "setPosRaw", at = @At("RETURN"))
    private void probe$moved(double x, double y, double z, CallbackInfo ci) {
        SleepEngine.onEntity((Entity) (Object) this, "entity-moved");
    }
}
