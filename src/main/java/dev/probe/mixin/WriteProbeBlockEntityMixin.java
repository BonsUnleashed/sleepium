package dev.probe.mixin;

import dev.probe.BeTickStats;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BlockEntity.setChanged(): inventory changes, furnace progress, comparator output (see {@link BeTickStats}). */
@Mixin(BlockEntity.class)
public abstract class WriteProbeBlockEntityMixin {
    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void probe$setChanged(CallbackInfo ci) {
        BeTickStats.writes++;
        if (dev.probe.optsupport.sleep.SleepEngine.ON) {
            BlockEntity be = (BlockEntity) (Object) this;
            dev.probe.optsupport.sleep.SleepEngine.onPos(be.getLevel(), be.getBlockPos(), "changed");
        }
    }

    /** The static variant: hoppers after a transfer, furnaces when a lit state or an item changes. */
    @Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
    private static void probe$setChangedStatic(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                               net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
        BeTickStats.writes++;
        if (dev.probe.optsupport.sleep.SleepEngine.ON) dev.probe.optsupport.sleep.SleepEngine.onPos(level, pos, "changed");
    }
}
