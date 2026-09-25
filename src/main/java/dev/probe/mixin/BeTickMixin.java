package dev.probe.mixin;

import dev.probe.BeTickStats;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wraps the single call site through which every block-entity type ticks (see {@link BeTickStats}). */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BeTickMixin {
    @Shadow @Final private BlockEntity blockEntity;

    private static final String TICK = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V";

    @Inject(method = "tick", at = @At(value = "INVOKE", target = TICK))
    private void probe$before(CallbackInfo ci) {
        if (BeTickStats.ON) BeTickStats.before(blockEntity);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = TICK, shift = At.Shift.AFTER))
    private void probe$after(CallbackInfo ci) {
        if (BeTickStats.ON) BeTickStats.after();
    }
}
