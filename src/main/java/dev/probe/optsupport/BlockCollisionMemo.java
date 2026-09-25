package dev.probe.optsupport;

import net.minecraft.world.phys.AABB;

/**
 * Per-entity memo of the block part of noCollision / noBlockCollision (BlockCollisionMemoMixin): the query box, and the
 * chunk sections covering the scanned block range with their modification counters when the answer was computed.
 * Valid only while every state in that range had a provably static collision shape (ShapePurity).
 */
public final class BlockCollisionMemo {
    public static final int MAX_SECTIONS = 8;

    public double minX, minY, minZ, maxX, maxY, maxZ;
    public final Object[] sections = new Object[MAX_SECTIONS];
    public final int[] mods = new int[MAX_SECTIONS];
    public int n;
    public boolean valid, free;

    public static long hits, misses, unmemoizable;

    public boolean sameBox(AABB b) {
        return b.minX == minX && b.minY == minY && b.minZ == minZ && b.maxX == maxX && b.maxY == maxY && b.maxZ == maxZ;
    }

    public void setBox(AABB b) {
        minX = b.minX;
        minY = b.minY;
        minZ = b.minZ;
        maxX = b.maxX;
        maxY = b.maxY;
        maxZ = b.maxZ;
    }

    /** Implemented on Entity. */
    public interface Holder {
        BlockCollisionMemo probe$collisionMemo();

        void probe$setCollisionMemo(BlockCollisionMemo m);
    }

    /** Implemented on LevelChunkSection: bumped on every block-state write. */
    public interface Counted {
        int probe$mod();
    }

    /** Implemented on BlockState (ShapeCacheMixin): collision shape proven to depend on the state alone. */
    public interface StaticShape {
        boolean probe$staticShape();
    }
}
