package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SleepProbeObserveMixin's load part for the one ticking vanilla block entity whose load(CompoundTag) does not call
 * super.load: the sculk catalyst (its cursors are replaced inside the same spreader object, which verifyAsleep, comparing
 * references by identity, does not see). 1.20.1 only: 1.21's loadWithComponents is final and covers every subclass.
 * (BrushableBlockEntity and ChiseledBookShelfBlockEntity skip super.load too, but do not tick.) Sleep engine only.
 */
@Mixin(SculkCatalystBlockEntity.class)
public abstract class SleepProbeObserveCatalystMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private void sleep$load(CompoundTag tag, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }
}
