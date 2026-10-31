package dev.sleep.opt;

import dev.sleep.optsupport.ParallelTickScout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** exp/parallel-tick-scout (measurement only): block reads and writes of a task. */
@Mixin(LevelChunk.class)
public abstract class ScoutChunkMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"))
    private void probe$read(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        ParallelTickScout.read(pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(method = "getFluidState(III)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"))
    private void probe$readFluid(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        ParallelTickScout.read(x, y, z);
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"))
    private void probe$readBe(BlockPos pos, LevelChunk.EntityCreationType t, CallbackInfoReturnable<?> cir) {
        ParallelTickScout.read(pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void probe$write(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        ParallelTickScout.write(pos.getX(), pos.getY(), pos.getZ());
    }
}
