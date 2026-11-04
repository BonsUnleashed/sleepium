package dev.lull.opt;

import dev.lull.optsupport.ReactsHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NeighborReactMixin's MultiNeighborUpdate part on 1.20.1: there runNext calls BlockState.neighborChanged itself (1.21:
 * through NeighborUpdater.executeUpdate, which NeighborReactMixin redirects). Same rule: a state that never reacts to a
 * neighbour change is not asked.
 */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate")
public abstract class NeighborReactMultiMixin {
    @Redirect(method = "runNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;neighborChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"))
    private void sleep$maybeChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean moved) {
        if (((ReactsHolder) state).sleep$reacts()) state.neighborChanged(level, pos, block, neighborPos, moved);
    }
}
