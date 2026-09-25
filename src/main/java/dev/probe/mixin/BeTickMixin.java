package dev.probe.mixin;

import dev.probe.BeTickStats;
import dev.probe.optsupport.sleep.SleepEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The single call site through which every block-entity type ticks: {@code ticker.tick(...)} inside
 * {@code LevelChunk$BoundTickingBlockEntity.tick}, reached only when vanilla really ticks (not removed, chunk ticking,
 * valid state). One redirect hosts the idle-tick detector (BeTickStats) and the sleep engine (SleepEngine):
 * a sleeping block entity simply does not call its ticker. With both off this is a plain call.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BeTickMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void probe$tick(BlockEntityTicker ticker, Level level, BlockPos pos, BlockState state, BlockEntity be) {
        if (SleepEngine.ON && SleepEngine.skip(be)) return;
        if (BeTickStats.ON) BeTickStats.before(be);
        if (SleepEngine.ON) SleepEngine.before(be);
        ticker.tick(level, pos, state, be);
        if (SleepEngine.ON) SleepEngine.after(be);
        if (BeTickStats.ON) BeTickStats.after();
    }
}
