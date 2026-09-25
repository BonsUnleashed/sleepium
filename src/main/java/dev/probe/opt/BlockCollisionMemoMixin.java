package dev.probe.opt;

import dev.probe.optsupport.BlockCollisionMemo;
import dev.probe.optsupport.OptCounters;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
        if (!probe$blocksFree(entity, collisionBox)) return false;
        if (!((CollisionGetter) (Object) this).getEntityCollisions(entity, collisionBox).isEmpty()) return false;
        if (entity == null) return true;
        WorldBorder border = this.getWorldBorder();   // CollisionGetter.borderCollision, inlined (private there)
        VoxelShape vs = border.isInsideCloseToBorder(entity, collisionBox) ? border.getCollisionShape() : null;
        return vs == null || !Shapes.joinIsNotEmpty(vs, Shapes.create(collisionBox), BooleanOp.AND);
    }

    /** Overrides the CollisionGetter default. */
    public boolean noBlockCollision(@Nullable Entity entity, AABB boundingBox) {
        return probe$blocksFree(entity, boundingBox);
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
        if (entity == null || this.isClientSide()) return probe$vanillaBlocksFree(entity, box);
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || Thread.currentThread() != server.getRunningThread()) return probe$vanillaBlocksFree(entity, box);
        // the block range BlockCollisions scans (its constructor)
        int x0 = Mth.floor(box.minX - 1.0E-7) - 1, x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int y0 = Mth.floor(box.minY - 1.0E-7) - 1, y1 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int z0 = Mth.floor(box.minZ - 1.0E-7) - 1, z1 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        BlockCollisionMemo.Holder holder = (BlockCollisionMemo.Holder) entity;
        BlockCollisionMemo m = holder.probe$collisionMemo();
        if (m != null && m.valid && m.sameBox(box) && probe$unchanged(m, x0, x1, y0, y1, z0, z1)) {
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
        m.valid = probe$record(m, x0, x1, y0, y1, z0, z1);
        if (m.valid) {
            m.setBox(box);
            m.free = free;
        } else {
            BlockCollisionMemo.unmemoizable++;
        }
        return free;
    }

    /** Record the sections covering the range; false if any state in it lacks a static shape or a chunk is missing. */
    @Unique
    private boolean probe$record(BlockCollisionMemo m, int x0, int x1, int y0, int y1, int z0, int z1) {
        int k = 0;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                BlockGetter g = ((CollisionGetter) (Object) this).getChunkForCollisions(cx, cz);
                if (!(g instanceof LevelChunk chunk) || chunk.getLevel().isDebug()) return false;
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int idx = chunk.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= chunk.getSections().length) continue;   // outside the world: always air
                    if (k == BlockCollisionMemo.MAX_SECTIONS) return false;
                    LevelChunkSection s = chunk.getSection(idx);
                    if (!s.hasOnlyAir()) {
                        int ax = Math.max(x0, cx << 4), bx = Math.min(x1, (cx << 4) + 15);
                        int ay = Math.max(y0, sy << 4), by = Math.min(y1, (sy << 4) + 15);
                        int az = Math.max(z0, cz << 4), bz = Math.min(z1, (cz << 4) + 15);
                        for (int x = ax; x <= bx; x++)
                            for (int y = ay; y <= by; y++)
                                for (int z = az; z <= bz; z++)
                                    if (!((BlockCollisionMemo.StaticShape) s.getBlockState(x & 15, y & 15, z & 15)).probe$staticShape()) return false;
                    }
                    m.sections[k] = s;
                    m.mods[k] = ((BlockCollisionMemo.Counted) s).probe$mod();
                    k++;
                }
            }
        }
        m.n = k;
        return true;
    }

    @Unique
    private boolean probe$unchanged(BlockCollisionMemo m, int x0, int x1, int y0, int y1, int z0, int z1) {
        int k = 0;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                BlockGetter g = ((CollisionGetter) (Object) this).getChunkForCollisions(cx, cz);
                if (!(g instanceof LevelChunk chunk)) return false;
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int idx = chunk.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= chunk.getSections().length) continue;
                    if (k == m.n) return false;
                    LevelChunkSection s = chunk.getSection(idx);
                    if (m.sections[k] != s || m.mods[k] != ((BlockCollisionMemo.Counted) s).probe$mod()) return false;
                    k++;
                }
            }
        }
        return k == m.n;
    }
}
