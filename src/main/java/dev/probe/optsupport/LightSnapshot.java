package dev.probe.optsupport;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * lab.opt.LightSnapshotMixin (server and client light engines): the light storage's published snapshot without copying
 * the whole section map each time.
 *
 * <p>Vanilla publishes what the light thread computed by copying its section map (every stored section of every
 * loaded chunk: ~18.5k entries here) into the "visible" map other threads read - after every light batch that changed
 * anything: 330 copies a second while generating, for ~16 changed sections each, ~130 MB/s of garbage (19 % of all
 * allocation during teleports, 10-08). The DataLayers themselves are already copy-on-write (a published layer is
 * copied before the light thread writes it again), only the map is copied whole.
 *
 * <p>Here the updating map remembers which keys it wrote since its last full copy (setLayer, removeLayer,
 * copyDataLayer: the map's only writes), and a snapshot is that full copy (the "base", never written again) plus a
 * small delta of those keys' current values (a tombstone for removed ones). A read looks in the delta, then the base:
 * the same key-to-layer mapping a full copy would give, the same DataLayer objects. When the delta outgrows
 * sqrt(32 x base) entries a new full copy becomes the base. {@code -Dlab.lightsnap.verify=true}: each snapshot
 * compared with a full copy, key by key.
 */
public final class LightSnapshot {
    private LightSnapshot() {}

    public static final boolean VERIFY = Boolean.getBoolean("lab.lightsnap.verify");
    public static final AtomicLong snapshots = new AtomicLong(), fullCopies = new AtomicLong(), deltaEntries = new AtomicLong(), mismatches = new AtomicLong();
    /** a removed key in a delta */
    static final DataLayer TOMB = new DataLayer();

    /** DataLayerStorageMap, through LightSnapshotMixin */
    public interface Tracked {
        Long2ObjectOpenHashMap<DataLayer> probe$snapshot(Long2ObjectOpenHashMap<DataLayer> live);
    }

    /** per updating map: its base, the keys written since, and the snapshot to publish */
    public static final class Track {
        Long2ObjectOpenHashMap<DataLayer> base;
        final LongOpenHashSet dirty = new LongOpenHashSet();

        public void written(long key) {
            if (base != null) dirty.add(key);
        }

        public Long2ObjectOpenHashMap<DataLayer> snapshot(Long2ObjectOpenHashMap<DataLayer> live) {
            snapshots.incrementAndGet();
            Long2ObjectOpenHashMap<DataLayer> out;
            if (base == null || dirty.size() > Math.max(64, (int) Math.sqrt(32.0 * base.size()))) {
                out = live.clone();
                base = out;
                dirty.clear();
                fullCopies.incrementAndGet();
            } else {
                Long2ObjectOpenHashMap<DataLayer> delta = new Long2ObjectOpenHashMap<>(dirty.size());
                for (var it = dirty.iterator(); it.hasNext(); ) {
                    long k = it.nextLong();
                    DataLayer v = live.get(k);
                    delta.put(k, v == null ? TOMB : v);
                }
                deltaEntries.addAndGet(delta.size());
                out = new DeltaMap(base, delta);
            }
            if (VERIFY) verify(out, live);
            return out;
        }
    }

    private static void verify(Long2ObjectOpenHashMap<DataLayer> snap, Long2ObjectOpenHashMap<DataLayer> live) {
        boolean bad = snap.size() != live.size();
        for (var e : live.long2ObjectEntrySet()) if (snap.get(e.getLongKey()) != e.getValue()) bad = true;
        if (snap instanceof DeltaMap d) for (var it = d.delta.keySet().iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            if (snap.get(k) != live.get(k) || snap.containsKey(k) != live.containsKey(k)) bad = true;
        }
        if (bad && mismatches.incrementAndGet() <= 5) System.out.println("[probe/opt] light snapshot MISMATCH (size " + snap.size() + " vs " + live.size() + ")");
    }

    public static String report() {
        long s = snapshots.get(), f = fullCopies.get();
        return String.format(java.util.Locale.ROOT, "light snapshots: %d published, %d full copies, %.0f delta entries per delta snapshot%s",
                s, f, s - f == 0 ? 0 : (double) deltaEntries.get() / (s - f), VERIFY ? ", mismatches " + mismatches.get() : "");
    }

    /**
     * A published snapshot: base + delta, read-only. Reads (get / containsKey / size) answer from the two maps; any
     * other use of it as a map gets a merged copy, built once.
     */
    static final class DeltaMap extends Long2ObjectOpenHashMap<DataLayer> {
        final Long2ObjectOpenHashMap<DataLayer> base, delta;
        private final int size;
        private volatile Long2ObjectOpenHashMap<DataLayer> merged;

        DeltaMap(Long2ObjectOpenHashMap<DataLayer> base, Long2ObjectOpenHashMap<DataLayer> delta) {
            super(1);
            this.base = base;
            this.delta = delta;
            int n = base.size();
            for (var it = delta.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                var e = it.next();
                boolean inBase = base.containsKey(e.getLongKey());
                if (e.getValue() == TOMB) { if (inBase) n--; } else if (!inBase) n++;
            }
            this.size = n;
        }

        @Override
        public DataLayer get(long k) {
            DataLayer v = delta.get(k);
            if (v == null) return base.get(k);
            return v == TOMB ? null : v;
        }

        @Override
        public boolean containsKey(long k) {
            DataLayer v = delta.get(k);
            if (v == null) return base.containsKey(k);
            return v != TOMB;
        }

        @Override
        public DataLayer getOrDefault(long k, DataLayer d) {
            return containsKey(k) ? get(k) : d;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        private Long2ObjectOpenHashMap<DataLayer> merged() {
            Long2ObjectOpenHashMap<DataLayer> m = merged;
            if (m == null) {
                m = base.clone();
                for (var it = delta.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                    var e = it.next();
                    if (e.getValue() == TOMB) m.remove(e.getLongKey());
                    else m.put(e.getLongKey(), e.getValue());
                }
                merged = m;
            }
            return m;
        }

        @Override
        public Long2ObjectMap.FastEntrySet<DataLayer> long2ObjectEntrySet() {
            return merged().long2ObjectEntrySet();
        }

        @Override
        public LongSet keySet() {
            return merged().keySet();
        }

        @Override
        public ObjectCollection<DataLayer> values() {
            return merged().values();
        }

        @Override
        public boolean containsValue(Object v) {
            return merged().containsValue(v);
        }

        @Override
        public Long2ObjectOpenHashMap<DataLayer> clone() {
            return merged().clone();
        }

        @Override
        public DataLayer put(long k, DataLayer v) {
            throw new UnsupportedOperationException("a published light snapshot is read-only");
        }

        @Override
        public DataLayer remove(long k) {
            throw new UnsupportedOperationException("a published light snapshot is read-only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("a published light snapshot is read-only");
        }
    }
}
