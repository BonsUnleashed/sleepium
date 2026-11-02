package dev.lull.opt;

import dev.lull.optsupport.ParallelTickScout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** exp/parallel-tick-scout (measurement only): the task boundaries of ServerLevel's tick. */
@Mixin(ServerLevel.class)
public abstract class ScoutLevelMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$tick(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        ParallelTickScout.beginTick(((ServerLevel) (Object) this).random);
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void sleep$entityStart(Entity e, CallbackInfo ci) {
        ParallelTickScout.begin(ParallelTickScout.ENTITY, e.getBlockX(), e.getBlockY(), e.getBlockZ(), e.getId());
        ParallelTickScout.entityWrite(e.getBlockX(), e.getBlockY(), e.getBlockZ());
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void sleep$entityEnd(Entity e, CallbackInfo ci) {
        ParallelTickScout.entityWrite(e.getBlockX(), e.getBlockY(), e.getBlockZ());
        ParallelTickScout.end();
    }

    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void sleep$chunkStart(LevelChunk chunk, int speed, CallbackInfo ci) {
        ParallelTickScout.begin(ParallelTickScout.CHUNK, chunk.getPos().getMinBlockX() + 8, 64, chunk.getPos().getMinBlockZ() + 8, chunk.getPos().toLong());
    }

    @Inject(method = "tickChunk", at = @At("RETURN"))
    private void sleep$chunkEnd(LevelChunk chunk, int speed, CallbackInfo ci) {
        ParallelTickScout.end();
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void sleep$blockStart(BlockPos pos, Block block, CallbackInfo ci) {
        ParallelTickScout.begin(ParallelTickScout.SCHEDULED, pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void sleep$blockEnd(BlockPos pos, Block block, CallbackInfo ci) {
        ParallelTickScout.end();
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void sleep$fluidStart(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        ParallelTickScout.begin(ParallelTickScout.SCHEDULED, pos.getX(), pos.getY(), pos.getZ());
    }

    @Inject(method = "tickFluid", at = @At("RETURN"))
    private void sleep$fluidEnd(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        ParallelTickScout.end();
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"))
    private void sleep$eventStart(BlockEventData ev, CallbackInfoReturnable<Boolean> cir) {
        ParallelTickScout.begin(ParallelTickScout.BLOCK_EVENT, ev.pos().getX(), ev.pos().getY(), ev.pos().getZ());
    }

    @Inject(method = "doBlockEvent", at = @At("RETURN"))
    private void sleep$eventEnd(BlockEventData ev, CallbackInfoReturnable<Boolean> cir) {
        ParallelTickScout.end();
    }
}
