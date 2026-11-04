package dev.lull.mixin;

import dev.lull.optsupport.sleep.SleepEngine;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SleepProbeContainerMixin's hopper part: HopperBlockEntity overrides setItem and removeItem (removeItemNoUpdate is
 * RandomizableContainerBlockEntity's, covered there). One mixin class on 1.21.1; split on 1.20.1 because the targets
 * declare different method sets there.
 */
@Mixin(HopperBlockEntity.class)
public abstract class SleepProbeContainerHopperMixin {
    private void sleep$wake() {
        BlockEntity be = (BlockEntity) (Object) this;
        SleepEngine.onPos(be.getLevel(), be.getBlockPos(), "container");
    }

    @Inject(method = "setItem", at = @At("HEAD"), require = 0)
    private void sleep$setItem(int slot, net.minecraft.world.item.ItemStack stack, CallbackInfo ci) {
        sleep$wake();
    }

    @Inject(method = "removeItem", at = @At("HEAD"), require = 0)
    private void sleep$removeItem(int slot, int count, CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        sleep$wake();
    }
}
