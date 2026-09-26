package dev.probe.opt;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import dev.probe.optsupport.FluidCaches;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/fluid-spread: the spread search of every flowing fluid (water, lava, modded FlowingFluids) without allocation.
 * JFR, continuous water/lava flow: 95 % of the scene in getSpread / getSlopeDistance, a quarter of it in per-call
 * Short2ObjectOpenHashMap / Short2BooleanOpenHashMap / Pair and in the occlusion LRU (ThreadLocal, LinkedHashMap, a new
 * key object per call).
 *
 * <p>Both are memo caches of pure lookups, so their representation cannot change a result: the per-call caches become
 * generation-stamped arrays indexed by vanilla's own 16-bit cache key (computed on first access, in the same order as
 * computeIfAbsent), the occlusion LRU a direct-mapped table keyed by the two states and the direction (vanilla caches
 * only static shapes, where the answer depends on nothing else). Server thread only; a fluid class that overrides
 * getSpread or getSlopeDistance, or foreign mixins on them, keep vanilla's code.
 */
@Mixin(FlowingFluid.class)
public abstract class FluidSpreadMixin {
    @Shadow public abstract Fluid getFlowing();
    @Shadow protected abstract int getSlopeFindDistance(LevelReader level);
    @Shadow protected abstract FluidState getNewLiquid(Level level, BlockPos pos, BlockState blockState);
    @Shadow protected abstract int getSlopeDistance(LevelReader level, BlockPos spreadPos, int distance, Direction direction, BlockState currentSpreadState,
            BlockPos sourcePos, Short2ObjectMap<Pair<BlockState, FluidState>> stateCache, Short2BooleanMap waterHoleCache);
    @Shadow private boolean canPassThrough(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, Direction direction, BlockPos spreadPos,
            BlockState spreadState, FluidState fluidState) { throw new AssertionError(); }
    @Shadow private boolean isWaterHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        throw new AssertionError();
    }
    @Shadow private static short getCacheKey(BlockPos sourcePos, BlockPos spreadPos) { throw new AssertionError(); }
    @Shadow @org.spongepowered.asm.mixin.Final private static ThreadLocal<it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap<net.minecraft.world.level.block.Block.BlockStatePairKey>> OCCLUSION_CACHE;

    /**
     * @author probe (lab.opt.FluidSpreadMixin)
     * @reason occlusion memo without a key object per call (same answers: pure function of static shapes)
     */
    @Overwrite
    private boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        boolean cacheable = !state.getBlock().hasDynamicShape() && !spreadState.getBlock().hasDynamicShape();
        if (cacheable && FluidCaches.onServerThread()) {
            int r = FluidCaches.occlusion(state, spreadState, direction);
            if (r >= 0) return r != 0;
            VoxelShape a = state.getCollisionShape(level, pos), b = spreadState.getCollisionShape(level, spreadPos);
            boolean flag = !Shapes.mergedFaceOccludes(a, b, direction);
            FluidCaches.putOcclusion(state, spreadState, direction, flag);
            return flag;
        }
        // vanilla, with its LRU (other threads, dynamic shapes)
        it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap<net.minecraft.world.level.block.Block.BlockStatePairKey> lru = cacheable ? OCCLUSION_CACHE.get() : null;
        net.minecraft.world.level.block.Block.BlockStatePairKey key;
        if (lru != null) {
            key = new net.minecraft.world.level.block.Block.BlockStatePairKey(state, spreadState, direction);
            byte b0 = lru.getAndMoveToFirst(key);
            if (b0 != 127) return b0 != 0;
        } else {
            key = null;
        }
        VoxelShape voxelshape1 = state.getCollisionShape(level, pos);
        VoxelShape voxelshape = spreadState.getCollisionShape(level, spreadPos);
        boolean flag = !Shapes.mergedFaceOccludes(voxelshape1, voxelshape, direction);
        if (lru != null) {
            if (lru.size() == 200) lru.removeLastByte();
            lru.putAndMoveToFirst(key, (byte) (flag ? 1 : 0));
        }
        return flag;
    }

    /**
     * @author probe (lab.opt.FluidSpreadMixin)
     * @reason the same search with stamped arrays instead of per-call hash maps and pairs
     */
    @Overwrite
    protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {
        if (!FluidCaches.fastSpread(this.getClass()) || !FluidCaches.onServerThread()) return probe$vanillaSpread(level, pos, state);
        FluidCaches c = FluidCaches.begin();
        if (c == null) return probe$vanillaSpread(level, pos, state);   // nested
        try {
            int i = 1000;
            Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos blockpos = pos.relative(direction);
                int key = getCacheKey(pos, blockpos) & 0xFFFF;
                if (!c.hasState(key)) {
                    BlockState bs = level.getBlockState(blockpos);
                    c.putState(key, bs, bs.getFluidState());
                }
                BlockState blockstate = c.state(key);
                FluidState fluidstate = c.fluid(key);
                FluidState fluidstate1 = this.getNewLiquid(level, blockpos, blockstate);
                if (this.canPassThrough(level, fluidstate1.getType(), pos, state, direction, blockpos, blockstate, fluidstate)) {
                    BlockPos blockpos1 = blockpos.below();
                    if (!c.hasHole(key)) {
                        BlockState below = level.getBlockState(blockpos1);
                        c.putHole(key, this.isWaterHole(level, this.getFlowing(), blockpos, blockstate, blockpos1, below));
                    }
                    int j = c.hole(key) ? 0 : probe$slope(level, blockpos, 1, direction.getOpposite(), blockstate, pos, c);
                    if (j < i) map.clear();
                    if (j <= i) {
                        map.put(direction, fluidstate1);
                        i = j;
                    }
                }
            }
            return map;
        } finally {
            FluidCaches.end();
        }
    }

    /** vanilla getSlopeDistance on the stamped caches */
    @org.spongepowered.asm.mixin.Unique
    private int probe$slope(LevelReader level, BlockPos spreadPos, int distance, Direction from, BlockState currentSpreadState, BlockPos sourcePos, FluidCaches c) {
        int i = 1000;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != from) {
                BlockPos blockpos = spreadPos.relative(direction);
                int key = getCacheKey(sourcePos, blockpos) & 0xFFFF;
                if (!c.hasState(key)) {
                    BlockState bs = level.getBlockState(blockpos);
                    c.putState(key, bs, bs.getFluidState());
                }
                BlockState blockstate = c.state(key);
                FluidState fluidstate = c.fluid(key);
                if (this.canPassThrough(level, this.getFlowing(), spreadPos, currentSpreadState, direction, blockpos, blockstate, fluidstate)) {
                    if (!c.hasHole(key)) {
                        BlockPos blockpos1 = blockpos.below();
                        BlockState below = level.getBlockState(blockpos1);
                        c.putHole(key, this.isWaterHole(level, this.getFlowing(), blockpos, blockstate, blockpos1, below));
                    }
                    if (c.hole(key)) return distance;
                    if (distance < this.getSlopeFindDistance(level)) {
                        int j = probe$slope(level, blockpos, distance + 1, direction.getOpposite(), blockstate, sourcePos, c);
                        if (j < i) i = j;
                    }
                }
            }
        }
        return i;
    }

    /** vanilla getSpread, verbatim (maps and pairs) */
    @org.spongepowered.asm.mixin.Unique
    private Map<Direction, FluidState> probe$vanillaSpread(Level level, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> short2objectmap = new Short2ObjectOpenHashMap<>();
        Short2BooleanMap short2booleanmap = new Short2BooleanOpenHashMap();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction);
            short short1 = getCacheKey(pos, blockpos);
            Pair<BlockState, FluidState> pair = short2objectmap.computeIfAbsent(short1, k -> {
                BlockState blockstate1 = level.getBlockState(blockpos);
                return Pair.of(blockstate1, blockstate1.getFluidState());
            });
            BlockState blockstate = pair.getFirst();
            FluidState fluidstate = pair.getSecond();
            FluidState fluidstate1 = this.getNewLiquid(level, blockpos, blockstate);
            if (this.canPassThrough(level, fluidstate1.getType(), pos, state, direction, blockpos, blockstate, fluidstate)) {
                BlockPos blockpos1 = blockpos.below();
                boolean flag = short2booleanmap.computeIfAbsent(short1, k -> {
                    BlockState blockstate1 = level.getBlockState(blockpos1);
                    return this.isWaterHole(level, this.getFlowing(), blockpos, blockstate, blockpos1, blockstate1);
                });
                int j = flag ? 0 : this.getSlopeDistance(level, blockpos, 1, direction.getOpposite(), blockstate, pos, short2objectmap, short2booleanmap);
                if (j < i) map.clear();
                if (j <= i) {
                    map.put(direction, fluidstate1);
                    i = j;
                }
            }
        }
        return map;
    }
}
