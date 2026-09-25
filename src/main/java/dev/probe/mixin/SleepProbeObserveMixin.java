package dev.probe.mixin;

import dev.probe.optsupport.sleep.SleepEngine;
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
    private void probe$materialize() {
        SleepEngine.materialize((BlockEntity) (Object) this);
    }

    @Inject(method = "saveWithFullMetadata", at = @At("HEAD"))
    private void probe$saveFull(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { probe$materialize(); }

    @Inject(method = "saveWithId", at = @At("HEAD"))
    private void probe$saveId(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { probe$materialize(); }

    @Inject(method = "saveWithoutMetadata", at = @At("HEAD"))
    private void probe$saveNoMeta(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { probe$materialize(); }

    @Inject(method = "saveCustomOnly", at = @At("HEAD"))
    private void probe$saveCustom(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { probe$materialize(); }

    @Inject(method = "saveCustomAndMetadata", at = @At("HEAD"))
    private void probe$saveCustomMeta(HolderLookup.Provider r, CallbackInfoReturnable<CompoundTag> cir) { probe$materialize(); }

    @Inject(method = "loadWithComponents", at = @At("HEAD"))
    private void probe$load(CompoundTag tag, HolderLookup.Provider r, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }

    @Inject(method = "loadCustomOnly", at = @At("HEAD"))
    private void probe$loadCustom(CompoundTag tag, HolderLookup.Provider r, CallbackInfo ci) {
        SleepEngine.onLoad((BlockEntity) (Object) this);
    }
}
