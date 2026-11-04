package dev.lull.optsupport;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * WorldgenShare: NoiseChunk.preliminarySurfaceLevel(column) is a pure function of the RandomState, the noise settings
 * and the quart column while the chunk's Blender is empty (no old chunks to blend with): a top-down scan of
 * initialDensityWithoutJaggedness, whose NoiseChunk caches are transparent for single-point contexts. Vanilla keeps it
 * per NoiseChunk, and the aquifer asks for columns up to 3 chunks away: ~150 columns computed per chunk for 16
 * unique ones. This table shares them across chunks and threads.
 *
 * <p>Direct-mapped, 2^17 slots, lock-free: one long per slot packs the quart column (24 + 24 bits), the value (12 bits)
 * and a valid bit, so a reader sees a whole entry or none; a collision overwrites (the value is recomputed later,
 * identically).
 */
public final class SurfaceColumns {
    public static final int MISS = Integer.MIN_VALUE;
    private static final int BITS = 17, MASK = (1 << BITS) - 1;
    private static final VarHandle AA = MethodHandles.arrayElementVarHandle(long[].class);
    private static final Map<RandomState, ArrayList<SurfaceColumns>> TABLES = new WeakHashMap<>();

    private final int minY, height, cellHeight;
    private final long[] slots = new long[1 << BITS];

    private SurfaceColumns(int minY, int height, int cellHeight) {
        this.minY = minY;
        this.height = height;
        this.cellHeight = cellHeight;
    }

    public static synchronized SurfaceColumns forState(RandomState state, int minY, int height, int cellHeight) {
        ArrayList<SurfaceColumns> l = TABLES.computeIfAbsent(state, k -> new ArrayList<>(1));
        for (SurfaceColumns t : l) if (t.minY == minY && t.height == height && t.cellHeight == cellHeight) return t;
        SurfaceColumns t = new SurfaceColumns(minY, height, cellHeight);
        l.add(t);
        return t;
    }

    private static int slot(int qx, int qz) {
        int h = qx * 0x9E3779B1 ^ qz * 0x85EBCA77;
        return (h ^ (h >>> 15)) & MASK;
    }

    private static long key(int qx, int qz) {
        return ((long) (qx & 0xFFFFFF) << 37) | ((long) (qz & 0xFFFFFF) << 13);
    }

    /** block x, z already quart-aligned (vanilla's QuartPos.toBlock(QuartPos.fromBlock(x))) */
    public int get(int x, int z) {
        int qx = x >> 2, qz = z >> 2;
        long e = (long) AA.getOpaque(slots, slot(qx, qz));
        if ((e & 1) == 0 || (e & ~0x1FFFL) != key(qx, qz)) return MISS;
        int v = (int) (e >>> 1) & 0xFFF;
        return v == 0xFFF ? Integer.MAX_VALUE : minY + v * cellHeight;
    }

    public void put(int x, int z, int level) {
        int v;
        if (level == Integer.MAX_VALUE) v = 0xFFF;
        else {
            int d = level - minY;
            if (d < 0 || d % cellHeight != 0 || d / cellHeight >= 0xFFF) return;   // not representable: leave to vanilla
            v = d / cellHeight;
        }
        int qx = x >> 2, qz = z >> 2;
        AA.setOpaque(slots, slot(qx, qz), key(qx, qz) | ((long) v << 1) | 1L);
    }
}
