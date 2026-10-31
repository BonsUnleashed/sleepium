package dev.sleep.opt;

import dev.sleep.optsupport.sleep.MoveMemo;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.MoveMemo: a modification counter on SynchedEntityData, moved on where set() changes a value. */
@Mixin(SynchedEntityData.class)
public abstract class MoveMemoDataMixin implements MoveMemo.DataEpoch {
    @Unique private int probe$dataEpoch;

    @Override
    public int probe$dataEpoch() {
        return probe$dataEpoch;
    }

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;setValue(Ljava/lang/Object;)V"))
    private void probe$changed(EntityDataAccessor<?> key, Object value, boolean force, CallbackInfo ci) {
        probe$dataEpoch++;
    }
}
