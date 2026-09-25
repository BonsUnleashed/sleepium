package dev.probe.mixin;

import dev.probe.optsupport.sleep.SleepEngine;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An open menu calls stillValid every tick and reads the block entity's fields (e.g. a furnace's progress bar):
 * treat it as a heartbeat "someone is watching" and keep the block entity awake and exact. Sleep engine only.
 */
@Mixin(BaseContainerBlockEntity.class)
public abstract class SleepProbeViewMixin {
    @Inject(method = "stillValid", at = @At("HEAD"))
    private void probe$viewed(Player player, CallbackInfoReturnable<Boolean> cir) {
        SleepEngine.viewed((BlockEntity) (Object) this);
    }
}
