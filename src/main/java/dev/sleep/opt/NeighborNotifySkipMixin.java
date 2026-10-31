package dev.sleep.opt;

import dev.sleep.optsupport.CompositeEventBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/neighbor-notify-skip: ServerLevel.updateNeighborsAt(...) builds, for every call, getBlockState(pos) +
 * EnumSet.allOf(Direction) + a NeighborNotifyEvent and posts it (1 800 per tick in the redstone scene), even with no
 * listener. Without listeners the event can neither be cancelled nor observed, so skipping it is exact.
 * Needs the composite bus (NeoForgeBusMixin) to ask "any listener?" cheaply; otherwise vanilla runs.
 */
@Mixin(ServerLevel.class)
public abstract class NeighborNotifySkipMixin {
    private static boolean sleep$noListeners() {
        return NeoForge.EVENT_BUS instanceof CompositeEventBus bus && !bus.hasListeners(BlockEvent.NeighborNotifyEvent.class);
    }

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"), cancellable = true)
    private void sleep$skip(BlockPos pos, Block block, CallbackInfo ci) {
        if (sleep$noListeners()) {
            ((LevelNeighborUpdaterAccessor) this).sleep$neighborUpdater().updateNeighborsAtExceptFromFacing(pos, block, null);
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void sleep$skipExcept(BlockPos pos, Block block, Direction skipSide, CallbackInfo ci) {
        if (sleep$noListeners()) {
            ((LevelNeighborUpdaterAccessor) this).sleep$neighborUpdater().updateNeighborsAtExceptFromFacing(pos, block, skipSide);
            ci.cancel();
        }
    }
}
