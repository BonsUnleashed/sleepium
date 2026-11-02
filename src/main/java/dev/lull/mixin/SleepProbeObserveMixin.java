package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.core.HolderLookup;
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
 */
@Mixin(BlockEntity.class)
public abstract class SleepProbeObserveMixin {
    private void sleep$materialize() {
        SleepEngine.materialize((BlockEntity) (Object) this);
    }

    @Inject(method = "saveWithFullMetadata", at = @At("HEAD"))
    private void sleep$saveFull(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveWithId", at = @At("HEAD"))
    private void sleep$saveId(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveWithoutMetadata", at = @At("HEAD"))
    private void sleep$saveNoMeta(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveCustomOnly", at = @At("HEAD"))
    private void sleep$saveCustom(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "saveCustomAndMetadata", at = @At("HEAD"))
    private void sleep$saveCustomMeta(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { sleep$materialize(); }

    @Inject(method = "loadWithComponents", at = @At("HEAD"))
    private void sleep$load(CompoundTag tag, HolderLookup.Provider r, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }

    @Inject(method = "loadCustomOnly", at = @At("HEAD"))
    private void sleep$loadCustom(CompoundTag tag, HolderLookup.Provider r, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }
}
