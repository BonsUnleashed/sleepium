package dev.probe.optsupport;

import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.AABB;

/**
 * Exact spatial index for one dense entity section (EntitySectionIndexMixin).
 *
 * <p>A grid of 4-block cells covering the section volume ± {@value #E} blocks. Vanilla queries a section when the
 * query box is within 2 blocks of it, so entities whose boxes stick out of their section must stay findable; boxes
 * and queries are clamped into the grid, which keeps a superset. Each cell holds a bitset over the entity's position
 * in the section's backing list. A query ORs the bitsets of the cells it overlaps and walks the bits in ascending
 * order, i.e. exactly vanilla's list order, applying vanilla's own intersects test before the consumer, so results
 * and their order are identical to the full scan, with a fraction of the tests.
 *
 * <p>Maintenance: bounding-box changes update the entity's cells incrementally (Entity.setBoundingBox hook);
 * section add/remove shift list positions, so they mark the index dirty and it is rebuilt before the next query.
 */
public final class SectionIndex {
    static final int E = 4, CELL = 4, N = (16 + 2 * E) / CELL;   // 6 cells per axis
    // scratch bitsets by nesting depth (a consumer may run another query): indices are used by their owner thread only
    private static long[][] pool = new long[8][];
    private static int depth;

    private final int ox, oy, oz;
    private int words;
    private long[] bits = new long[0];
    private final IdentityHashMap<Object, int[]> recs = new IdentityHashMap<>();
    private boolean dirty = true;
    public final Thread owner = Thread.currentThread();
    public static long rebuilds, updates, queries;

    public SectionIndex(long sectionKey) {
        ox = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)) - E;
        oy = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)) - E;
        oz = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)) - E;
    }

    public void markDirty() {
        dirty = true;
    }

    private static int cell(double coord, int origin) {
        int c = Math.floorDiv(Mth.floor(coord) - origin, CELL);
        return c < 0 ? 0 : Math.min(c, N - 1);
    }

    private void fill(AABB bb, int[] r) {
        r[1] = cell(bb.minX, ox); r[2] = cell(bb.maxX, ox);
        r[3] = cell(bb.minY, oy); r[4] = cell(bb.maxY, oy);
        r[5] = cell(bb.minZ, oz); r[6] = cell(bb.maxZ, oz);
    }

    private void apply(int[] r, boolean set) {
        int pos = r[0], w = pos >>> 6;
        long m = 1L << (pos & 63);
        for (int x = r[1]; x <= r[2]; x++)
            for (int y = r[3]; y <= r[4]; y++)
                for (int z = r[5]; z <= r[6]; z++) {
                    int i = ((x * N + y) * N + z) * words + w;
                    if (set) bits[i] |= m; else bits[i] &= ~m;
                }
    }

    private void rebuild(List<? extends EntityAccess> list) {
        int n = list.size();
        words = Math.max(1, (n + 63) >>> 6);
        int need = N * N * N * words;
        if (bits.length < need) bits = new long[need];
        else java.util.Arrays.fill(bits, 0, need, 0L);
        recs.clear();
        for (int i = 0; i < n; i++) {
            EntityAccess e = list.get(i);
            int[] r = new int[7];
            r[0] = i;
            fill(e.getBoundingBox(), r);
            apply(r, true);
            recs.put(e, r);
        }
        dirty = false;
        rebuilds++;
    }

    /** Entity.setBoundingBox: move the entity's bits to its new cells. */
    public void update(Object entity, AABB bb) {
        if (dirty) return;
        int[] r = recs.get(entity);
        if (r == null) { dirty = true; return; }
        int x1 = cell(bb.minX, ox), x2 = cell(bb.maxX, ox), y1 = cell(bb.minY, oy), y2 = cell(bb.maxY, oy), z1 = cell(bb.minZ, oz), z2 = cell(bb.maxZ, oz);
        if (x1 == r[1] && x2 == r[2] && y1 == r[3] && y2 == r[4] && z1 == r[5] && z2 == r[6]) return;
        apply(r, false);
        r[1] = x1; r[2] = x2; r[3] = y1; r[4] = y2; r[5] = z1; r[6] = z2;
        apply(r, true);
        updates++;
    }

    /** Same result, same order as vanilla's scan of {@code list} with bounds.intersects. */
    public <T extends EntityAccess> AbortableIterationConsumer.Continuation query(List<T> list, AABB bounds, AbortableIterationConsumer<T> consumer) {
        if (dirty || recs.size() != list.size()) rebuild(list);
        queries++;
        int x1 = cell(bounds.minX, ox), x2 = cell(bounds.maxX, ox), y1 = cell(bounds.minY, oy), y2 = cell(bounds.maxY, oy), z1 = cell(bounds.minZ, oz), z2 = cell(bounds.maxZ, oz);
        int d = depth++;
        try {
            if (d >= pool.length) pool = java.util.Arrays.copyOf(pool, pool.length * 2);
            long[] acc = pool[d];
            if (acc == null || acc.length < words) pool[d] = acc = new long[Math.max(words, 8)];
            return scan(list, bounds, consumer, acc, x1, x2, y1, y2, z1, z2);
        } finally {
            depth--;
        }
    }

    private <T extends EntityAccess> AbortableIterationConsumer.Continuation scan(List<T> list, AABB bounds, AbortableIterationConsumer<T> consumer,
                                                                                long[] acc, int x1, int x2, int y1, int y2, int z1, int z2) {
        java.util.Arrays.fill(acc, 0, words, 0L);
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    int base = ((x * N + y) * N + z) * words;
                    for (int w = 0; w < words; w++) acc[w] |= bits[base + w];
                }
        int n = list.size();
        for (int w = 0; w < words; w++) {
            long v = acc[w];
            while (v != 0) {
                int idx = (w << 6) + Long.numberOfTrailingZeros(v);
                v &= v - 1;
                if (idx >= n) return AbortableIterationConsumer.Continuation.CONTINUE;
                T t = list.get(idx);
                if (t.getBoundingBox().intersects(bounds) && consumer.accept(t).shouldAbort()) return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }
}
