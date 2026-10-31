package dev.sleep.opt;

import dev.sleep.optsupport.ParallelTickScout;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** exp/parallel-tick-scout (measurement only): entity queries by box are reads of their sections. */
@Mixin(EntitySectionStorage.class)
public abstract class ScoutMiscMixin {
    @Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"))
    private void sleep$query(AABB box, net.minecraft.util.AbortableIterationConsumer<?> consumer, CallbackInfo ci) {
        ParallelTickScout.readBox(box);
    }
}
