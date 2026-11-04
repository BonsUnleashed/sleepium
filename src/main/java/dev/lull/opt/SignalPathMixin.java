package dev.lull.opt;

import dev.lull.optsupport.SignalProof;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/signal-path: the redstone signal queries of every Level without a new BlockPos per neighbour. Vanilla's
 * SignalGetter defaults allocate pos.relative(dir) for each of the 6 neighbours, and 6 more per conductor neighbour
 * (getDirectSignalTo): 75 % of the allocation in the redstone scene (JFR).
 *
 * <p>Same algorithm as the defaults: same order (SignalGetter.DIRECTIONS / below-above-north-south-west-east), same
 * early exits, same calls. The neighbour position is a reused MutableBlockPos (one per nesting depth) only when the
 * neighbour's state is proven by SignalProof not to retain it; otherwise it gets an immutable copy, as in vanilla.
 * Positions handed in by callers are passed on untouched. Server thread only; anything else takes the vanilla code.
 */
// 1.20.1: the mixin implements SignalGetter so that the jar's reobfuscation renames the four overrides below to the
// SRG names of SignalGetter's defaults (Forge production runs m_..._ names; plain methods of a mixin are neither in
// the refmap nor renamed, and would be merged into Level under their Mojang names, overriding nothing). 1.21: Mojang
// names at run time, no interface needed.
@Mixin(Level.class)
public abstract class SignalPathMixin implements SignalGetter {
    @Shadow public abstract boolean isClientSide();
    @Shadow public abstract BlockState getBlockState(BlockPos pos);

    @Unique private static final Direction[] DIRS = SignalGetter.DIRECTIONS;
    @Unique private static final Direction[] TO_ORDER = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    @Unique private BlockPos.MutableBlockPos[] sleep$scratch = new BlockPos.MutableBlockPos[8];
    @Unique private int sleep$depth;
    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.signalCheck");

    @Unique
    private void sleep$check(String what, int fast, int vanilla) {
        dev.lull.optsupport.OptCounters.SIGNAL_CHECKED.incrementAndGet();
        if (fast != vanilla && dev.lull.optsupport.OptCounters.SIGNAL_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[sleep/opt] signal path MISMATCH " + what + ": fast " + fast + " vanilla " + vanilla);
    }

    /** vanilla getBestNeighborSignal, for the shadow check */
    @Unique
    private int sleep$vanillaBest(BlockPos pos) {
        int i = 0;
        for (Direction d : DIRS) {
            BlockPos p = pos.relative(d);
            BlockState st = this.getBlockState(p);
            int j = st.getSignal((Level) (Object) this, p, d);
            if (st.shouldCheckWeakPower((Level) (Object) this, p, d)) j = Math.max(j, sleep$vanillaDirectTo(p));
            if (j >= 15) return 15;
            if (j > i) i = j;
        }
        return i;
    }

    @Unique
    private boolean sleep$fast() {
        if (!SignalProof.WRAPPERS_UNTOUCHED || this.isClientSide()) return false;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && Thread.currentThread() == server.getRunningThread();
    }

    @Unique
    private BlockPos.MutableBlockPos sleep$push() {
        int d = sleep$depth++;
        BlockPos.MutableBlockPos[] a = sleep$scratch;
        if (d == a.length) sleep$scratch = a = java.util.Arrays.copyOf(a, d * 2);
        BlockPos.MutableBlockPos s = a[d];
        if (s == null) a[d] = s = new BlockPos.MutableBlockPos();
        return s;
    }

    /** vanilla getSignal(pos.relative(d), d) for the neighbour at (x, y, z) */
    @Unique
    private int sleep$signalAt(int x, int y, int z, Direction d) {
        BlockPos.MutableBlockPos s = sleep$push();
        try {
            s.set(x, y, z);
            BlockState st = this.getBlockState(s);
            BlockPos p = SignalProof.signalOk(st) ? s : s.immutable();
            int i = st.getSignal((Level) (Object) this, p, d);
            return st.shouldCheckWeakPower((Level) (Object) this, p, d) ? Math.max(i, sleep$directTo(x, y, z)) : i;
        } finally {
            sleep$depth--;
        }
    }

    /** vanilla getDirectSignalTo(pos): below, above, north, south, west, east, stop at 15 */
    @Unique
    private int sleep$directTo(int x, int y, int z) {
        BlockPos.MutableBlockPos s = sleep$push();
        try {
            int i = 0;
            for (Direction d : TO_ORDER) {
                s.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                BlockState st = this.getBlockState(s);
                i = Math.max(i, st.getDirectSignal((Level) (Object) this, SignalProof.directOk(st) ? s : s.immutable(), d));
                if (i >= 15) return i;
            }
            return i;
        } finally {
            sleep$depth--;
        }
    }

    // ---- overrides of the SignalGetter defaults (same results, fewer objects)

    public int getSignal(BlockPos pos, Direction direction) {
        BlockState st = this.getBlockState(pos);
        int i = st.getSignal((Level) (Object) this, pos, direction);
        if (!st.shouldCheckWeakPower((Level) (Object) this, pos, direction)) return i;
        return Math.max(i, sleep$fast() ? sleep$directTo(pos.getX(), pos.getY(), pos.getZ()) : sleep$vanillaDirectTo(pos));
    }

    public int getDirectSignalTo(BlockPos pos) {
        if (!sleep$fast()) return sleep$vanillaDirectTo(pos);
        int r = sleep$directTo(pos.getX(), pos.getY(), pos.getZ());
        if (CHECK) sleep$check("directTo", r, sleep$vanillaDirectTo(pos));
        return r;
    }

    public boolean hasNeighborSignal(BlockPos pos) {
        if (!sleep$fast()) {
            SignalGetter g = (SignalGetter) (Object) this;
            return g.getSignal(pos.below(), Direction.DOWN) > 0 || g.getSignal(pos.above(), Direction.UP) > 0
                    || g.getSignal(pos.north(), Direction.NORTH) > 0 || g.getSignal(pos.south(), Direction.SOUTH) > 0
                    || g.getSignal(pos.west(), Direction.WEST) > 0 || g.getSignal(pos.east(), Direction.EAST) > 0;
        }
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (Direction d : TO_ORDER) if (sleep$signalAt(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ(), d) > 0) return true;
        return false;
    }

    public int getBestNeighborSignal(BlockPos pos) {
        boolean fast = sleep$fast();
        if (fast && CHECK) {
            int v = sleep$vanillaBest(pos);
            int r = sleep$best(pos, true);
            sleep$check("best", r, v);
            return r;
        }
        return sleep$best(pos, fast);
    }

    @Unique
    private int sleep$best(BlockPos pos, boolean fast) {
        int i = 0;
        for (Direction d : DIRS) {
            int j = fast ? sleep$signalAt(pos.getX() + d.getStepX(), pos.getY() + d.getStepY(), pos.getZ() + d.getStepZ(), d)
                    : ((SignalGetter) (Object) this).getSignal(pos.relative(d), d);
            if (j >= 15) return 15;
            if (j > i) i = j;
        }
        return i;
    }

    @Unique
    private int sleep$vanillaDirectTo(BlockPos pos) {
        SignalGetter g = (SignalGetter) (Object) this;
        int i = 0;
        for (Direction d : TO_ORDER) {
            BlockPos q = pos.relative(d);
            i = Math.max(i, g.getDirectSignal(q, d));
            if (i >= 15) return i;
        }
        return i;
    }
}
