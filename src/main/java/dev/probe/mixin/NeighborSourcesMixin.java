package dev.probe.mixin;

import dev.probe.EventSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neighbour/shape update requests. CollectingNeighborUpdater is the level's updater on both sides, so the counts
 * include the client level in singleplayer (small: the client does not run redstone).
 */
@Mixin(CollectingNeighborUpdater.class)
public abstract class NeighborSourcesMixin {
    @Inject(method = "shapeUpdate", at = @At("HEAD"))
    private void probe$shape(Direction d, BlockState s, BlockPos p, BlockPos n, int flags, int rec, CallbackInfo ci) {
        EventSources.shapeUpdates++;
    }

    @Inject(method = "neighborChanged(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void probe$neighbor(BlockPos p, Block b, BlockPos n, CallbackInfo ci) {
        EventSources.neighborUpdates++;
    }

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V", at = @At("HEAD"))
    private void probe$neighbor2(BlockState s, BlockPos p, Block b, BlockPos n, boolean moved, CallbackInfo ci) {
        EventSources.neighborUpdates++;
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"))
    private void probe$neighbors6(BlockPos p, Block b, Direction except, CallbackInfo ci) {
        EventSources.neighborUpdates += except == null ? 6 : 5;
    }
}
