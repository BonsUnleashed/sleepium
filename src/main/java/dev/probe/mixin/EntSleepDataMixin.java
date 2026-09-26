package dev.probe.mixin;

import dev.probe.optsupport.sleep.EntitySleep;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Entity sleep: a change of an entity's synched data (item, flags, health, pose, ...) is an entity event: it wakes the
 * entity itself if asleep and every sleeper whose entity reads cover it. Pass-through, no allocation.
 */
@Mixin(SynchedEntityData.class)
public abstract class EntSleepDataMixin {
    @Shadow @Final private SyncedDataHolder entity;

    @ModifyVariable(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"), argsOnly = true)
    private Object probe$set(Object value, EntityDataAccessor<?> key, Object same, boolean force) {
        if (EntitySleep.ON && entity instanceof Entity e) EntitySleep.onDataSet(e, key, value);
        return value;
    }
}
