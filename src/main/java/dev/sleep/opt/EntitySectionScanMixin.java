package dev.sleep.opt;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import dev.sleep.optsupport.OptCounters;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/section-scan: allocation-free, exact entity-section walk for every entity query in the game.
 *
 * <p>Vanilla {@code forEachAccessibleNonEmptySection} walks, per section column x, {@code sectionIds.subSet(x-column)}:
 * two allocations (subset view + iterator) and a pass over <i>every</i> section of that column, filtering y and z.
 * Here the candidate keys of the box (usually 1-4 per column) are generated directly, sorted exactly like the
 * LongAVLTreeSet orders them (signed long order of SectionPos.asLong), and looked up in the section map. Same
 * sections, same order, so the same entities in the same order. Very large boxes fall back to vanilla.
 * {@code -Dlab.opt.sectionScanCheck=true} runs vanilla alongside and counts any difference.
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionScanMixin<T extends EntityAccess> {
    @Shadow @Final private Long2ObjectMap<EntitySection<T>> sections;
    @Shadow @Final private LongSortedSet sectionIds;

    private static final int MAX_CANDIDATES = 64;
    private static final boolean CHECK = Boolean.getBoolean("lab.opt.sectionScanCheck");
    private static final ThreadLocal<long[]> BUF = ThreadLocal.withInitial(() -> new long[MAX_CANDIDATES]);

    /**
     * @author probe (lab.opt.EntitySectionScanMixin)
     * @reason exact walk without subSet/iterator for dense section maps. The vanilla loop stays inline and the
     *         consumer never reaches a non-inlined call on the common path; otherwise the capturing lambdas of
     *         getEntities/forConsumer are no longer scalar-replaced (measured +28 KiB/tick in the piston scene).
     *         An @Inject would allocate a CallbackInfo per query.
     */
    @Overwrite
    public void forEachAccessibleNonEmptySection(AABB bb, AbortableIterationConsumer<EntitySection<T>> consumer) {
        int j = SectionPos.posToSectionCoord(bb.minX - 2.0);
        int k = SectionPos.posToSectionCoord(bb.minY - 4.0);
        int l = SectionPos.posToSectionCoord(bb.minZ - 2.0);
        int i1 = SectionPos.posToSectionCoord(bb.maxX + 2.0);
        int j1 = SectionPos.posToSectionCoord(bb.maxY + 0.0);
        int k1 = SectionPos.posToSectionCoord(bb.maxZ + 2.0);
        // the consumer is passed only inside the rarely-taken branch: C2 has no partial escape analysis, so a
        // non-inlined call receiving it on the common path would force the capturing lambdas onto the heap
        if (sleep$useDense(j, i1, k, j1, l, k1)) {
            sleep$dense(j, i1, k, j1, l, k1, consumer);
            return;
        }
        for (int l1 = j; l1 <= i1; l1++) {
            long i2 = SectionPos.asLong(l1, 0, 0);
            long j2 = SectionPos.asLong(l1, -1, -1);
            LongIterator longiterator = this.sectionIds.subSet(i2, j2 + 1L).iterator();
            while (longiterator.hasNext()) {
                long k2 = longiterator.nextLong();
                int l2 = SectionPos.y(k2);
                int i3 = SectionPos.z(k2);
                if (l2 >= k && l2 <= j1 && i3 >= l && i3 <= k1) {
                    EntitySection<T> entitysection = this.sections.get(k2);
                    if (entitysection != null && !entitysection.isEmpty() && entitysection.getStatus().isAccessible()
                            && consumer.accept(entitysection).shouldAbort()) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Dense section map and a small box: generate the candidate keys, sort them in LongAVLTreeSet order (signed long
     * order of SectionPos.asLong) and look them up directly.
     */
    private boolean sleep$useDense(int x0, int x1, int y0, int y1, int z0, int z1) {
        long ny = (long) y1 - y0 + 1, nz = (long) z1 - z0 + 1;
        if (ny <= 0 || nz <= 0 || x1 < x0 || ny * nz > MAX_CANDIDATES || sections.size() <= ny * nz * (x1 - x0 + 1)) {
            if (CHECK) OptCounters.SECTION_FALLBACK.incrementAndGet();
            return false;
        }
        return true;
    }

    private void sleep$dense(int x0, int x1, int y0, int y1, int z0, int z1, AbortableIterationConsumer<EntitySection<T>> consumer) {
        if (CHECK) {
            OptCounters.SECTION_FAST.incrementAndGet();
            check(x0, x1, y0, y1, z0, z1);
        }
        long[] keys = BUF.get();
        for (int x = x0; x <= x1; x++) {
            int n = 0;
            for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) keys[n++] = SectionPos.asLong(x, y, z);
            for (int i = 1; i < n; i++) {
                long kk = keys[i];
                int jj = i - 1;
                while (jj >= 0 && keys[jj] > kk) {
                    keys[jj + 1] = keys[jj];
                    jj--;
                }
                keys[jj + 1] = kk;
            }
            for (int i = 0; i < n; i++) {
                EntitySection<T> s = sections.get(keys[i]);
                if (s != null && !s.isEmpty() && s.getStatus().isAccessible() && consumer.accept(s).shouldAbort()) return;
            }
        }
    }

    /** Shadow check: the section sequence of the vanilla walk must equal ours (only non-empty accessible sections). */
    private void check(int x0, int x1, int y0, int y1, int z0, int z1) {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int x = x0; x <= x1; x++) {
            long lo = SectionPos.asLong(x, 0, 0), hi = SectionPos.asLong(x, -1, -1);
            LongIterator it = sectionIds.subSet(lo, hi + 1L).iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                int y = SectionPos.y(k), z = SectionPos.z(k);
                if (y >= y0 && y <= y1 && z >= z0 && z <= z1) {
                    EntitySection<T> s = sections.get(k);
                    if (s != null && !s.isEmpty() && s.getStatus().isAccessible()) a.append(k).append(',');
                }
            }
            long[] keys = new long[(y1 - y0 + 1) * (z1 - z0 + 1)];
            int n = 0;
            for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) keys[n++] = SectionPos.asLong(x, y, z);
            java.util.Arrays.sort(keys, 0, n);
            for (int i = 0; i < n; i++) {
                EntitySection<T> s = sections.get(keys[i]);
                if (s != null && !s.isEmpty() && s.getStatus().isAccessible()) b.append(keys[i]).append(',');
            }
        }
        OptCounters.SECTION_CHECKED.incrementAndGet();
        if (!a.toString().contentEquals(b)) {
            if (OptCounters.SECTION_MISMATCHES.incrementAndGet() <= 5) System.out.println("[sleep/opt] section scan MISMATCH vanilla=" + a + " ours=" + b);
        }
    }
}
