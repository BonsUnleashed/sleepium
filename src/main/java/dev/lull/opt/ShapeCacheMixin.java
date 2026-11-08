package dev.lull.opt;

import dev.lull.optsupport.BlockCollisionMemo;
import dev.lull.optsupport.OptCounters;
import dev.lull.optsupport.ShapePurity;
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
public abstract class ShapeCacheMixin implements BlockCollisionMemo.StaticShape {
    @Shadow public abstract Block getBlock();
    @Shadow protected abstract BlockState asState();
    @Shadow public abstract VoxelShape getCollisionShape(BlockGetter level, BlockPos pos);

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.shapeCheck");
    @Unique private VoxelShape sleep$collision;

    @Unique
    private void sleep$check(VoxelShape cached, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape v = ((BlockBehaviourInvoker) getBlock()).sleep$getCollisionShape(asState(), level, pos, context);
        OptCounters.SHAPE_CHECKED.incrementAndGet();
        if (v != cached && OptCounters.SHAPE_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[sleep/opt] shape cache MISMATCH " + asState() + " at " + pos + (v.equals(cached) ? " (equal, not same object)" : ""));
    }

    @Override
    public boolean sleep$staticShape() {
        return sleep$collision != null;
    }

    @Override
    public VoxelShape sleep$staticCollision() {
        return sleep$collision;
    }

    @Inject(method = "initCache", at = @At("TAIL"))
    private void sleep$initCache(CallbackInfo ci) {
        Block block = getBlock();
        // getCollisionShape(level, pos) returns cache.collisionShape whenever the cache exists (no dynamic shape)
        sleep$collision = !block.hasDynamicShape() && ShapePurity.isStatic(block)
                ? ((BlockBehaviourInvoker) block).sleep$getCollisionShape(asState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()) : null;
        // (the block itself, as the cache just built computed its collisionShape - the same object, its shape being static -
        // not getCollisionShape(level, pos), which reads the cache: ModernFix's lazy cache counts what initCache reaches as
        // cache state and puts its cache build at the head of every method reading it, its own build included: a
        // stack overflow at the first block state, production Forge 1.20.1 with ModernFix)
    }

    /**
     * @author probe (lab.opt.ShapeCacheMixin)
     * @reason cached collision shape for blocks whose shape is a pure function of the state
     */
    @Overwrite
    public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = sleep$collision;
        if (s != null) {
            if (CHECK) sleep$check(s, level, pos, context);
            return s;
        }
        return ((BlockBehaviourInvoker) getBlock()).sleep$getCollisionShape(asState(), level, pos, context);
    }
}
