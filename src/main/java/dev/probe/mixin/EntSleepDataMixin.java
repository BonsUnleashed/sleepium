package dev.probe.mixin;

import dev.probe.optsupport.sleep.EntitySleep;
import dev.probe.optsupport.sleep.EntityFp;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity sleep: a change of an entity's synched data (item, flags, health, pose, ...) is an entity event: it wakes the
 * entity itself if asleep and every sleeper whose entity reads cover it. Pass-through, no allocation. It also counts the
 * changes (a value replaced or forced, the dirty flags cleared by packDirty): a mob's observation compares the count
 * instead of fingerprinting every item (EntityFp.DataEpoch).
 */
@Mixin(SynchedEntityData.class)
public abstract class EntSleepDataMixin implements EntityFp.DataEpoch {
    @Shadow @Final private SyncedDataHolder entity;
    @Shadow @Final private SynchedEntityData.DataItem<?>[] itemsById;
    @Shadow private boolean isDirty;
    @Unique private int probe$epoch;
    @Unique private int probe$mutable = -1;   // values that can change in place (-1: not counted yet)

    @ModifyVariable(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"), argsOnly = true)
    private Object probe$set(Object value, EntityDataAccessor<?> key, Object same, boolean force) {
        if (EntitySleep.ON) {
            Object old = itemsById[key.id()].getValue();
            if (force || !java.util.Objects.equals(old, value)) {
                probe$epoch++;
                if (probe$mutable >= 0 && old != value) probe$mutable += (EntityFp.immutableValue(value) ? 0 : 1) - (EntityFp.immutableValue(old) ? 0 : 1);
            }
            if (entity instanceof Entity e) EntitySleep.onDataSet(e, key, value);
        }
        return value;
    }

    @Inject(method = "packDirty", at = @At("HEAD"))
    private void probe$packDirty(CallbackInfoReturnable<?> cir) {
        if (isDirty) probe$epoch++;   // the dirty flags are cleared
    }

    @Override
    public int probe$dataEpoch() {
        return probe$epoch;
    }

    @Override
    public boolean probe$mutableValues() {
        if (probe$mutable < 0) {
            int m = 0;
            for (SynchedEntityData.DataItem<?> it : itemsById) if (it != null && !EntityFp.immutableValue(it.getValue())) m++;
            probe$mutable = m;
        }
        return probe$mutable > 0;
    }
}
