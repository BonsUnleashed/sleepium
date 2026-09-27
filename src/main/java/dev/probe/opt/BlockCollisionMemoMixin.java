package dev.probe.opt;

import dev.probe.optsupport.BlockCollisionMemo;
import dev.probe.optsupport.OptCounters;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/collision-memo: resting entities ask the same block question every tick (items: noCollision on their deflated
 * box, ~36 block reads each; JFR 4000 items: 28 % of the server thread in BlockCollisions.computeNext). The block part
 * of noCollision / noBlockCollision is a pure function of the box and of the blocks in the scanned range when every
 * one of them has a provably static collision shape (ShapePurity; no level/pos/context/block-entity dependence).
 * So it is memoized per entity: same box (all six doubles), same LevelChunk sections (identity) with the same
 * modification counters (SectionModCountMixin), chunk still loaded -> same answer. Server thread only.
 * {@code -Dlab.opt.collisionMemoCheck=true} recomputes vanilla on every hit and counts differences.
 */
@Mixin(Level.class)
public abstract class BlockCollisionMemoMixin {
    @Shadow public abstract boolean isClientSide();
    @Shadow public abstract WorldBorder getWorldBorder();

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.collisionMemoCheck");

    /** Overrides the CollisionGetter default: same steps, block part memoized. */
    public boolean noCollision(@Nullable Entity entity, AABB collisionBox) {
        if (dev.probe.optsupport.sleep.SleepEngine.HOOKS) dev.probe.optsupport.sleep.SleepEngine.readRegion(collisionBox);
        if (!probe$blocksFree(entity, collisionBox)) return false;
        if (!((CollisionGetter) (Object) this).getEntityCollisions(entity, collisionBox).isEmpty()) return false;
        if (entity == null) return true;
        WorldBorder border = this.getWorldBorder();   // CollisionGetter.borderCollision, inlined (private there)
        VoxelShape vs = border.isInsideCloseToBorder(entity, collisionBox) ? border.getCollisionShape() : null;
        return vs == null || !Shapes.joinIsNotEmpty(vs, Shapes.create(collisionBox), BooleanOp.AND);
    }

    /** Overrides the CollisionGetter default. */
    public boolean noBlockCollision(@Nullable Entity entity, AABB boundingBox) {
        if (dev.probe.optsupport.sleep.SleepEngine.HOOKS) dev.probe.optsupport.sleep.SleepEngine.readRegion(boundingBox);
        return probe$blocksFree(entity, boundingBox);
    }

    /**
     * Overrides the CollisionGetter default (every on-ground entity, every tick): memoized per entity like noCollision,
     * keyed also by the entity position (the distances pick the block). Vanilla body on a miss.
     */
    public java.util.Optional<net.minecraft.core.BlockPos> findSupportingBlock(Entity entity, AABB box) {
        if (dev.probe.optsupport.sleep.SleepEngine.HOOKS) dev.probe.optsupport.sleep.SleepEngine.readRegion(box);
        boolean memo = BlockCollisionMemo.MOVE_SLOTS && BlockCollisionMemo.usable((Level) (Object) this);
        BlockCollisionMemo m = null;
        CollisionGetter self = (CollisionGetter) (Object) this;
        if (memo) {
            m = ((BlockCollisionMemo.Holder) entity).probe$supportMemo();
            if (m != null && m.valid && m.sameBox(box) && m.sameKey(entity.getX(), entity.getY(), entity.getZ()) && BlockCollisionMemo.unchanged(self, m, box)) {
                BlockCollisionMemo.supportHits++;
                @SuppressWarnings("unchecked")
                java.util.Optional<net.minecraft.core.BlockPos> r = (java.util.Optional<net.minecraft.core.BlockPos>) m.result;
                if (CHECK) {
                    OptCounters.MEMO_CHECKED.incrementAndGet();
                    if (!probe$vanillaSupport(entity, box).equals(r) && OptCounters.MEMO_MISMATCHES.incrementAndGet() <= 5)
                        System.out.println("[probe/opt] supporting-block memo MISMATCH for " + entity);
                }
                return r;
            }
            BlockCollisionMemo.supportMisses++;
        }
        java.util.Optional<net.minecraft.core.BlockPos> r = probe$vanillaSupport(entity, box);
        if (memo) {
            if (m == null) ((BlockCollisionMemo.Holder) entity).probe$setSupportMemo(m = new BlockCollisionMemo());
            m.valid = BlockCollisionMemo.worthRecording(m, box) && BlockCollisionMemo.record(self, m, box);
            if (m.valid) {
                m.setBox(box);
                m.setKey(entity.getX(), entity.getY(), entity.getZ());
                m.result = r;
            }
        }
        return r;
    }

    /** CollisionGetter.findSupportingBlock, verbatim */
    @Unique
    private java.util.Optional<net.minecraft.core.BlockPos> probe$vanillaSupport(Entity entity, AABB box) {
        net.minecraft.core.BlockPos blockpos = null;
        double d0 = Double.MAX_VALUE;
        net.minecraft.world.level.BlockCollisions<net.minecraft.core.BlockPos> blockcollisions =
                new net.minecraft.world.level.BlockCollisions<>((CollisionGetter) (Object) this, entity, box, false, (p, s) -> p);
        while (blockcollisions.hasNext()) {
            net.minecraft.core.BlockPos blockpos1 = blockcollisions.next();
            double d1 = blockpos1.distToCenterSqr(entity.position());
            if (d1 < d0 || d1 == d0 && (blockpos == null || blockpos.compareTo(blockpos1) < 0)) {
                blockpos = blockpos1.immutable();
                d0 = d1;
            }
        }
        return java.util.Optional.ofNullable(blockpos);
    }

    @Unique
    private boolean probe$vanillaBlocksFree(@Nullable Entity entity, AABB box) {
        for (VoxelShape vs : ((CollisionGetter) (Object) this).getBlockCollisions(entity, box)) {
            if (!vs.isEmpty()) return false;
        }
        return true;
    }

    @Unique
    private boolean probe$blocksFree(@Nullable Entity entity, AABB box) {
        if (entity == null || !BlockCollisionMemo.usable((Level) (Object) this)) return probe$vanillaBlocksFree(entity, box);
        CollisionGetter self = (CollisionGetter) (Object) this;
        BlockCollisionMemo.Holder holder = (BlockCollisionMemo.Holder) entity;
        BlockCollisionMemo m = holder.probe$collisionMemo();
        if (m != null && m.valid && m.sameBox(box) && BlockCollisionMemo.unchanged(self, m, box)) {
            BlockCollisionMemo.hits++;
            if (CHECK) {
                OptCounters.MEMO_CHECKED.incrementAndGet();
                if (probe$vanillaBlocksFree(entity, box) != m.free && OptCounters.MEMO_MISMATCHES.incrementAndGet() <= 5)
                    System.out.println("[probe/opt] collision memo MISMATCH for " + entity + " box " + box);
            }
            return m.free;
        }
        BlockCollisionMemo.misses++;
        boolean free = probe$vanillaBlocksFree(entity, box);
        if (m == null) holder.probe$setCollisionMemo(m = new BlockCollisionMemo());
        m.valid = BlockCollisionMemo.worthRecording(m, box) && BlockCollisionMemo.record(self, m, box);
        if (m.valid) {
            m.setBox(box);
            m.free = free;
        } else {
            BlockCollisionMemo.unmemoizable++;
        }
        return free;
    }

}
