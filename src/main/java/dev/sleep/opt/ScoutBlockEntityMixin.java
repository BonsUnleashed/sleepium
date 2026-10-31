package dev.sleep.opt;

import dev.sleep.optsupport.ParallelTickScout;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** exp/parallel-tick-scout (measurement only): a block entity tick is a task. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class ScoutBlockEntityMixin {
    @Shadow @Final private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$start(CallbackInfo ci) {
        var p = blockEntity.getBlockPos();
        ParallelTickScout.begin(ParallelTickScout.BLOCK_ENTITY, p.getX(), p.getY(), p.getZ());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void sleep$end(CallbackInfo ci) {
        ParallelTickScout.end();
    }
}
