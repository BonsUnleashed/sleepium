package dev.sleep.opt;

import dev.sleep.optsupport.ReactsHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/neighbor-react: skip the neighbour update of a block state whose block does not override neighborChanged
 * (air, stone, dirt, most decorative blocks, vanilla or modded): the call would only reach BlockBehaviour's
 * default, which sends a debug-renderer packet. Universal and exact for gameplay. JFR redstone scene: the
 * megamorphic handleNeighborChanged dispatch alone was ~9 % of the server thread.
 * Caveat: a mod that mixes into BlockBehaviour.neighborChanged itself would be bypassed.
 */
@Mixin(targets = {
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$MultiNeighborUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$SimpleNeighborUpdate",
        "net.minecraft.world.level.redstone.CollectingNeighborUpdater$FullNeighborUpdate"})
public abstract class NeighborReactMixin {
    @Redirect(method = "runNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/redstone/NeighborUpdater;executeUpdate(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V"))
    private void sleep$maybeUpdate(Level level, BlockState state, BlockPos pos, Block block, BlockPos neighborPos, boolean moved) {
        if (((ReactsHolder) state).sleep$reacts()) NeighborUpdater.executeUpdate(level, state, pos, block, neighborPos, moved);
    }
}
