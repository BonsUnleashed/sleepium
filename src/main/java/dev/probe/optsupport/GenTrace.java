package dev.probe.optsupport;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measurement (-Dlab.pgen.trace=true): the generation pipeline second by second — what ParallelGen was given, started
 * and finished, how long steps waited for a worker and FEATURES for their 3x3 window, how often generation tasks ran
 * (runUntilWait), how many chunks reached FULL. Printed as "[probe/gentrace]" once a second while anything moved.
 */
public final class GenTrace {
    public static final boolean ON = Boolean.getBoolean("lab.pgen.trace");

    public static final LongAdder submitted = new LongAdder(), started = new LongAdder(), finished = new LongAdder(),
            queueNs = new LongAdder(), runNs = new LongAdder(), featureWaits = new LongAdder(), featureWaitNs = new LongAdder(),
            taskRuns = new LongAdder(), taskRunNs = new LongAdder(), fulls = new LongAdder(), sent = new LongAdder(),
            clientChunks = new LongAdder(), clientLights = new LongAdder(), clientTicks = new LongAdder(), notAllRendered = new LongAdder(), batching = new LongAdder(), uploading = new LongAdder();
    public static volatile int toBatchMax;
    /** the chunk sender's state, sampled once a second on the main thread (GenTraceSendMixin) */
    public static volatile String sender = "";
    public static long lastSenderSample;
    public static final AtomicLong featureWaitMax = new AtomicLong(), queueMax = new AtomicLong();
    public static final AtomicInteger running = new AtomicInteger();

    /** per status: steps, wall ns from start to completion, max */
    private static final java.util.Map<String, long[]> STEPS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void step(String status, long ns) {
        long[] a = STEPS.computeIfAbsent(status, k -> new long[3]);
        synchronized (a) {
            a[0]++;
            a[1] += ns;
            if (ns > a[2]) a[2] = ns;
        }
    }

    private static String steps() {
        StringBuilder sb = new StringBuilder();
        for (var e : new java.util.TreeMap<>(STEPS).entrySet()) {
            long[] a = e.getValue();
            synchronized (a) {
                if (a[0] == 0) continue;
                sb.append(String.format(java.util.Locale.ROOT, " %s %d×%.1f/%.0f", e.getKey(), a[0], a[1] / 1e6 / a[0], a[2] / 1e6));
                a[0] = a[1] = a[2] = 0;
            }
        }
        return sb.toString();
    }

    /** server tick wall times of the current second (main thread writes, the trace thread swaps) */
    private static volatile long[] ticks = new long[64];
    private static volatile int tickN;

    public static void tick(long ns) {
        long[] t = ticks;
        int n = tickN;
        if (n < t.length) {
            t[n] = ns;
            tickN = n + 1;
        }
    }

    private static String ticks() {
        long[] t = ticks;
        int n = Math.min(tickN, t.length);
        ticks = new long[64];
        tickN = 0;
        if (n == 0) return "no ticks";
        long[] c = java.util.Arrays.copyOf(t, n);
        java.util.Arrays.sort(c);
        long sum = 0;
        for (long v : c) sum += v;
        return String.format(java.util.Locale.ROOT, "server ticks %d: avg %.1f, p90 %.1f, max %.1f ms", n, sum / 1e6 / n, c[(int) (n * 0.9)] / 1e6, c[n - 1] / 1e6);
    }

    private static long gcCount, gcMillis;

    /** collections and their time since the last call, the heap in use and committed */
    private static String gc() {
        long c = 0, ms = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            c += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        long dc = c - gcCount, dm = ms - gcMillis;
        gcCount = c;
        gcMillis = ms;
        var h = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return String.format(java.util.Locale.ROOT, "gc %d (%d ms), heap %d / %d MB", dc, dm, h.getUsed() >> 20, h.getCommitted() >> 20);
    }

    private GenTrace() {}

    public static void maxOf(AtomicLong a, long v) {
        a.accumulateAndGet(v, Math::max);
    }

    static {
        if (ON) {
            Thread t = new Thread(() -> {
                while (true) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                    long sub = submitted.sumThenReset(), st = started.sumThenReset(), fin = finished.sumThenReset();
                    long q = queueNs.sumThenReset(), r = runNs.sumThenReset(), fw = featureWaits.sumThenReset(), fwn = featureWaitNs.sumThenReset();
                    long tr = taskRuns.sumThenReset(), trn = taskRunNs.sumThenReset(), fu = fulls.sumThenReset(), se = sent.sumThenReset();
                    long fwm = featureWaitMax.getAndSet(0), qm = queueMax.getAndSet(0);
                    long cc = clientChunks.sumThenReset(), cl = clientLights.sumThenReset(), ct = clientTicks.sumThenReset(), nr = notAllRendered.sumThenReset(), bt = batching.sumThenReset(), up = uploading.sumThenReset();
                    int tbm = toBatchMax;
                    toBatchMax = 0;
                    if (sub + st + fin + tr + fu + se + cc + cl + nr + bt + up == 0) continue;
                    System.out.println("[probe/gentrace] " + ticks() + " | " + gc() + " | steps (count x avg/max ms):" + steps());
                    if (Boolean.getBoolean("lab.reactor.scout")) System.out.println("[probe/gentrace] " + MainReactor.scoutReport() + " | " + MainReactor.wakeReport());
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "[probe/gentrace] pool steps: given %d, started %d (to a worker %.2f ms avg, max %.1f), done %d (%.2f ms each), running %d, FEATURES waiting %d now, %d waited (%.1f ms avg, max %.1f) | tasks run %d (%.3f ms each) | FULL %d | sent %d, %s | client: chunks %d, light %d; of %d ticks not all rendered %d, batching %d (max %d), uploading %d",
                            sub, st, st == 0 ? 0 : q / 1e6 / st, qm / 1e6, fin, fin == 0 ? 0 : r / 1e6 / fin, running.get(), ParallelGen.waiting(),
                            fw, fw == 0 ? 0 : fwn / 1e6 / fw, fwm / 1e6, tr, tr == 0 ? 0 : trn / 1e6 / tr, fu, se, sender, cc, cl, ct, nr, bt, tbm, up));
                }
            }, "probe-gentrace");
            t.setDaemon(true);
            t.start();
        }
    }
}
