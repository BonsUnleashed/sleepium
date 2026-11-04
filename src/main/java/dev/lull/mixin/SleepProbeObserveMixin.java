package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * External observation of a sleeping block entity must see exact values: every save entry point first materializes
 * the lazily advanced clocks; every load resets the sleeper (the fields were replaced). Sleep engine only.
 *
 * <p>1.20.1: the save entry points are saveWithFullMetadata / saveWithId / saveWithoutMetadata (no registry argument;
 * 1.21's saveCustomOnly / saveCustomAndMetadata do not exist), the one load is load(CompoundTag), which subclasses
 * override and call first (1.21: loadWithComponents / loadCustomOnly, final).
 */
@Mixin(BlockEntity.class)
public abstract class SleepProbeObserveMixin {
    private void sleep$materialize() {
        SleepEngine.materialize((BlockEntity) (Object) this);
    }

    @Inject(method = "saveWithFullMetadata", at = @At("HEAD"))
    private void sleep$saveFull(CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveWithId", at = @At("HEAD"))
    private void sleep$saveId(CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveWithoutMetadata", at = @At("HEAD"))
    private void sleep$saveNoMeta(CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "load", at = @At("HEAD"))
    private void sleep$load(CompoundTag tag, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }
}
