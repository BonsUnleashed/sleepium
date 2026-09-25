package dev.probe.opt;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import dev.probe.optsupport.NeighborRecs;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/neighbor-queue: the neighbour-update queue of every Level without an object per update (the discrete-event core).
 *
 * <p>Vanilla allocates a record per update (Simple/Full/Shape/MultiNeighborUpdate) and pushes it on an ArrayDeque plus
 * a per-layer list; runNext is an interface call. Here the same stack and layer list are struct-of-arrays records with
 * a kind tag and a switch. The algorithm is vanilla's step by step: layer entries pushed in reverse, the top entry
 * stepped until something is added this layer, the chain limit with the same log line, everything cleared in finally.
 * Positions are stored as the very references vanilla stores (including its immutable() copies, and the one argument
 * vanilla keeps mutable), so every handler receives the same objects. Only the record objects disappear.
 */
@Mixin(CollectingNeighborUpdater.class)
public abstract class NeighborQueueMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private int maxChainedNeighborUpdates;

    @Unique private static final Logger PROBE_LOGGER = LogUtils.getLogger();
    @Unique private static final byte FULL = 0, MULTI = 1, SHAPE = 2, SIMPLE = 3;
    @Unique private static final Direction[] ORDER = NeighborUpdater.UPDATE_ORDER;
    @Unique private static final Direction[] DIRS = Direction.values();
    /** NeighborReactMixin's skip (its redirects sit in the record classes this queue no longer uses) */
    @Unique private static final boolean REACT = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.NeighborReactMixin");

    @Unique private int probe$count;

    // the stack (index size-1 = top) and the entries added during the current layer, same record layout
    @Unique private final NeighborRecs probe$stack = new NeighborRecs(), probe$added = new NeighborRecs();

    /**
     * @author probe (lab.opt.NeighborQueueMixin)
     * @reason object-free queue record, same order
     */
    @Overwrite
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel) {
        NeighborRecs r = probe$target(pos);
        if (r == null) return;
        int i = r.add();
        r.kind[i] = SHAPE;
        r.dir[i] = direction.ordinal();
        r.state[i] = state;
        r.pos[i] = pos.immutable();
        r.npos[i] = neighborPos.immutable();
        r.flags[i] = flags;
        r.limit[i] = recursionLevel;
        probe$run();
    }

    /**
     * @author probe (lab.opt.NeighborQueueMixin)
     * @reason object-free queue record, same order (vanilla keeps this pos argument as given)
     */
    @Overwrite
    public void neighborChanged(BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        NeighborRecs r = probe$target(pos);
        if (r == null) return;
        int i = r.add();
        r.kind[i] = SIMPLE;
        r.pos[i] = pos;
        r.block[i] = neighborBlock;
        r.npos[i] = neighborPos.immutable();
        probe$run();
    }

    /**
     * @author probe (lab.opt.NeighborQueueMixin)
     * @reason object-free queue record, same order
     */
    @Overwrite
    public void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        NeighborRecs r = probe$target(pos);
        if (r == null) return;
        int i = r.add();
        r.kind[i] = FULL;
        r.state[i] = state;
        r.pos[i] = pos.immutable();
        r.block[i] = neighborBlock;
        r.npos[i] = neighborPos.immutable();
        r.moved[i] = movedByPiston;
        probe$run();
    }

    /**
     * @author probe (lab.opt.NeighborQueueMixin)
     * @reason object-free queue record, same order
     */
    @Overwrite
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction facing) {
        NeighborRecs r = probe$target(pos);
        if (r == null) return;
        int i = r.add();
        r.kind[i] = MULTI;
        r.pos[i] = pos.immutable();
        r.block[i] = block;
        r.dir[i] = facing == null ? -1 : facing.ordinal();
        r.idx[i] = ORDER[0] == facing ? 1 : 0;   // vanilla MultiNeighborUpdate constructor
        probe$run();
    }

    /**
     * Vanilla addAndRun, first half: count, chain limit (same log line), and where the new record goes. Returns null when
     * it is skipped; probe$run() must still follow (it runs the queue when this was the first update).
     */
    @Unique
    private NeighborRecs probe$target(BlockPos pos) {
        boolean nested = probe$count > 0;
        boolean limited = maxChainedNeighborUpdates >= 0 && probe$count >= maxChainedNeighborUpdates;
        probe$count++;
        if (limited) {
            if (probe$count - 1 == maxChainedNeighborUpdates)
                PROBE_LOGGER.error("Too many chained neighbor updates. Skipping the rest. First skipped position: " + pos.toShortString());
            if (!nested) probe$runUpdates();
            return null;
        }
        probe$first = !nested;
        return nested ? probe$added : probe$stack;
    }

    @Unique private boolean probe$first;

    @Unique
    private void probe$run() {
        if (probe$first) {
            probe$first = false;
            probe$runUpdates();
        }
    }

    /** Vanilla runUpdates on the record arrays. */
    @Unique
    private void probe$runUpdates() {
        NeighborRecs stack = probe$stack, added = probe$added;
        try {
            while (stack.size > 0 || added.size > 0) {
                for (int i = added.size - 1; i >= 0; i--) added.copyTo(i, stack);
                added.clear();
                int top = stack.size - 1;
                while (added.size == 0) {
                    if (!probe$runNext(stack, top)) {
                        stack.release(top);
                        stack.size--;
                        break;
                    }
                }
            }
        } finally {
            stack.clear();
            added.clear();
            probe$count = 0;
        }
    }

    @Unique
    private static void probe$execute(Level lvl, BlockState state, BlockPos pos, Block block, BlockPos from, boolean moved) {
        if (REACT && !((dev.probe.optsupport.ReactsHolder) state).probe$reacts()) return;
        NeighborUpdater.executeUpdate(lvl, state, pos, block, from, moved);
    }

    /** The runNext of vanilla's four record types. */
    @Unique
    private boolean probe$runNext(NeighborRecs s, int i) {
        Level lvl = this.level;
        switch (s.kind[i]) {
            case FULL -> {
                probe$execute(lvl, (BlockState) s.state[i], (BlockPos) s.pos[i], (Block) s.block[i], (BlockPos) s.npos[i], s.moved[i]);
                return false;
            }
            case SHAPE -> {
                NeighborUpdater.executeShapeUpdate(lvl, DIRS[s.dir[i]], (BlockState) s.state[i], (BlockPos) s.pos[i], (BlockPos) s.npos[i], s.flags[i], s.limit[i]);
                return false;
            }
            case SIMPLE -> {
                BlockPos pos = (BlockPos) s.pos[i];
                probe$execute(lvl, lvl.getBlockState(pos), pos, (Block) s.block[i], (BlockPos) s.npos[i], false);
                return false;
            }
            default -> {   // MULTI
                BlockPos source = (BlockPos) s.pos[i];
                Block sourceBlock = (Block) s.block[i];
                int skip = s.dir[i];
                int k = s.idx[i];
                s.idx[i] = k + 1;   // written before the call: nothing touches the stack while it runs
                BlockPos p = source.relative(ORDER[k]);
                probe$execute(lvl, lvl.getBlockState(p), p, sourceBlock, source, false);
                k = s.idx[i];
                if (k < ORDER.length && skip >= 0 && ORDER[k].ordinal() == skip) s.idx[i] = ++k;
                return k < ORDER.length;
            }
        }
    }
}
