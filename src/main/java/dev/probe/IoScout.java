package dev.probe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measurement only (-Dprobe.ioScout=true): how the IOWorker mailboxes drain their write queues. Per storage type
 * (chunk / entities / poi): stores posted (producer), chunks written (consumer), time per write split into the whole
 * runStore (NBT + compression + disk), RegionFile.write (disk) and its header write, the pendingWrites size seen by the
 * writer. Logged every 5 s as "[probe/io]".
 */
public final class IoScout {
    public static final boolean ON = Boolean.getBoolean("probe.ioScout");

    public static final class Kind {
        final LongAdder stores = new LongAdder(), loads = new LongAdder(), reads = new LongAdder(), readNs = new LongAdder();
        final LongAdder writes = new LongAdder(), writeNs = new LongAdder(), diskNs = new LongAdder(), headerNs = new LongAdder();
        final AtomicLong writeMax = new AtomicLong(), diskMax = new AtomicLong();
        volatile int pending, pendingMax;
    }

    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();
    /** the storage type of the IOWorker whose writer runs on this thread (RegionFile does not know its worker) */
    private static final ThreadLocal<Kind> CURRENT = new ThreadLocal<>();

    private IoScout() {}

    public static Kind kind(String type) {
        return KINDS.computeIfAbsent(type, t -> new Kind());
    }

    public static void stored(String type) { kind(type).stores.increment(); }
    public static void loaded(String type) { kind(type).loads.increment(); }

    public static void read(String type, long ns) {
        Kind k = kind(type);
        k.reads.increment();
        k.readNs.add(ns);
    }

    private static final ThreadLocal<long[]> START = ThreadLocal.withInitial(() -> new long[1]);

    public static void beginWrite(String type) {
        CURRENT.set(kind(type));
        START.get()[0] = System.nanoTime();
    }

    public static void endWrite(int pending) {
        Kind k = CURRENT.get();
        CURRENT.remove();
        if (k == null) return;
        long ns = System.nanoTime() - START.get()[0];
        k.writes.increment();
        k.writeNs.add(ns);
        k.writeMax.accumulateAndGet(ns, Math::max);
        k.pending = pending;
        if (pending > k.pendingMax) k.pendingMax = pending;
    }

    /** ChunkIo's writer: one batch of n chunks */
    public static void batch(String type, int n, long ns, int pending) {
        Kind k = kind(type);
        k.writes.add(n);
        k.writeNs.add(ns);
        k.writeMax.accumulateAndGet(ns, Math::max);
        k.pending = pending;
        if (pending > k.pendingMax) k.pendingMax = pending;
    }

    public static void disk(long ns) {
        Kind k = CURRENT.get();
        if (k == null) return;
        k.diskNs.add(ns);
        k.diskMax.accumulateAndGet(ns, Math::max);
    }

    public static void header(long ns) {
        Kind k = CURRENT.get();
        if (k != null) k.headerNs.add(ns);
    }

    static {
        if (ON) {
            Thread t = new Thread(() -> {
                while (true) {
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                    StringBuilder sb = new StringBuilder("[probe/io] 5 s:");
                    KINDS.forEach((type, k) -> {
                        long st = k.stores.sumThenReset(), ld = k.loads.sumThenReset(), w = k.writes.sumThenReset(), rd = k.reads.sumThenReset(), rn = k.readNs.sumThenReset();
                        long wn = k.writeNs.sumThenReset(), dn = k.diskNs.sumThenReset(), hn = k.headerNs.sumThenReset();
                        long wm = k.writeMax.getAndSet(0), dm = k.diskMax.getAndSet(0);
                        int pm = k.pendingMax;
                        k.pendingMax = 0;
                        if (st == 0 && w == 0 && ld == 0 && k.pending == 0) return;
                        sb.append(String.format(" | %s: loads %d (from disk %d, %.2f ms each), stores %d, written %d (busy reads+writes %.0f%%), per write %.2f ms (max %.1f): disk %.2f ms (max %.1f), header %.2f ms; pending %d (max %d)",
                                type, ld, rd, rd == 0 ? 0 : rn / 1e6 / rd, st, w, (wn + rn) / 5e7, w == 0 ? 0 : wn / 1e6 / w, wm / 1e6, w == 0 ? 0 : dn / 1e6 / w, dm / 1e6, w == 0 ? 0 : hn / 1e6 / w, k.pending, pm));
                    });
                    ProbeMod.LOGGER.info(sb.toString());
                }
            }, "probe-io-scout");
            t.setDaemon(true);
            t.start();
        }
    }
}
