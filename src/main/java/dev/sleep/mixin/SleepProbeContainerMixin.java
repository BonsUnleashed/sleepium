package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.SleepEngine;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wake a sleeping block entity (and everything subscribed to its position) when its container contents change,
 * including paths without setChanged (RandomizableContainerBlockEntity/Hopper setItem). Only with the sleep engine.
 */
@Mixin({BaseContainerBlockEntity.class, RandomizableContainerBlockEntity.class, HopperBlockEntity.class, AbstractFurnaceBlockEntity.class})
public abstract class SleepProbeContainerMixin {
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

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), require = 0)
    private void sleep$removeItemNoUpdate(int slot, CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        sleep$wake();
    }
}
