package dev.probe.opt;

import dev.probe.optsupport.OptCounters;
import dev.probe.optsupport.ShapePurity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/shape-cache: the context-taking {@code getCollisionShape(level, pos, context)} (every entity collision, support
 * block and pathfinding query) never uses the state's cached collision shape: each call is a megamorphic
 * Block.getCollisionShape -> getShape chain. For blocks whose shape methods provably depend on nothing but the
 * state (ShapePurity: bytecode analysis of the resolved methods), the cached shape IS the result: the cache was
 * computed by the same pure method, returning the same object.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ShapeCacheMixin {
    @Shadow public abstract Block getBlock();
    @Shadow protected abstract BlockState asState();
    @Shadow public abstract VoxelShape getCollisionShape(BlockGetter level, BlockPos pos);

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.shapeCheck");
    @Unique private VoxelShape probe$collision;

    @Unique
    private void probe$check(VoxelShape cached, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape v = ((BlockBehaviourInvoker) getBlock()).probe$getCollisionShape(asState(), level, pos, context);
        OptCounters.SHAPE_CHECKED.incrementAndGet();
        if (v != cached && OptCounters.SHAPE_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] shape cache MISMATCH " + asState() + " at " + pos + (v.equals(cached) ? " (equal, not same object)" : ""));
    }

    @Inject(method = "initCache", at = @At("TAIL"))
    private void probe$initCache(CallbackInfo ci) {
        Block block = getBlock();
        // getCollisionShape(level, pos) returns cache.collisionShape whenever the cache exists (no dynamic shape)
        probe$collision = !block.hasDynamicShape() && ShapePurity.isStatic(block)
                ? getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) : null;
    }

    /**
     * @author probe (lab.opt.ShapeCacheMixin)
     * @reason cached collision shape for blocks whose shape is a pure function of the state
     */
    @Overwrite
    public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = probe$collision;
        if (s != null) {
            if (CHECK) probe$check(s, level, pos, context);
            return s;
        }
        return ((BlockBehaviourInvoker) getBlock()).probe$getCollisionShape(asState(), level, pos, context);
    }
}
