package dev.probe.opt;

import dev.probe.optsupport.ParallelTickScout;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** exp/parallel-tick-scout (measurement only): every constructed entity takes an id from the global counter. */
@Mixin(Entity.class)
public abstract class ScoutEntityMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void probe$constructed(EntityType<?> type, Level level, CallbackInfo ci) {
        ParallelTickScout.entityConstructed();
    }
}
