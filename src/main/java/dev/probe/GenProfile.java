package dev.probe;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measurement: wall time and calls per generation phase while a genbench runs, whatever thread runs it (worldgen
 * actor, Worker-Main, our pool, the light actor). Phases are keyed by the step's status name, so modded steps show
 * up by themselves. Wall time, not thread CPU: Windows thread CPU has 15.6 ms granularity.
 */
public final class GenProfile {
    private static final Map<String, LongAdder[]> PHASES = new ConcurrentHashMap<>();

    private GenProfile() {}

    public static boolean active() {
        return GenBenchHooks.active;
    }

    /** allocated bytes of the current thread so far (for add's `bytes`: end - start) */
    public static long allocated() {
        return ThreadAlloc.current();
    }

    public static void add(String phase, long nanos, long bytes) {
        add(phase, nanos, bytes, 0);
    }

    /** this thread's ImprovedNoise.noise calls so far (with -Dlab.genscout; for add's `perlin`: end - start) */
    public static long perlin() {
        return dev.probe.optsupport.GenScout.perlinCount();
    }

    public static void add(String phase, long nanos, long bytes, long perlin) {
        LongAdder[] a = PHASES.computeIfAbsent(phase, k -> new LongAdder[] {new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()});
        a[0].increment();
        a[1].add(nanos);
        a[2].add(bytes);
        if (perlin != 0) a[3].add(perlin);
    }

    /** one line per phase, most time first; per FULL chunk = total / chunks */
    static String reportAndReset(int chunks) {
        StringBuilder b = new StringBuilder("generation phases (wall, all threads):");
        long all = 0, mem = 0;
        for (var e : PHASES.entrySet())
            if (!e.getKey().startsWith("(inside)") && !e.getKey().startsWith("actor:") && !e.getKey().startsWith("main:")) {
                all += e.getValue()[1].sum();
                mem += e.getValue()[2].sum();
            }
        b.append(String.format(Locale.ROOT, " total %.1f ms, %.1f MB allocated per FULL chunk", all / 1e6 / chunks, mem / 1e6 / chunks));
        PHASES.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[1].sum(), x.getValue()[1].sum())).forEach(e -> {
            long n = e.getValue()[0].sum(), t = e.getValue()[1].sum(), m = e.getValue()[2].sum(), pn = e.getValue()[3].sum();
            b.append(String.format(Locale.ROOT, "%n  %-28s %6d calls %8.1f us/call %6.2f ms per FULL chunk %7.2f MB/call %7.2f MB per FULL chunk",
                    e.getKey(), n, t / 1e3 / Math.max(1, n), t / 1e6 / chunks, m / 1e6 / Math.max(1, n), m / 1e6 / chunks));
            if (pn != 0) b.append(String.format(Locale.ROOT, " %9.0f perlin/call %9.0f perlin per FULL chunk", (double) pn / Math.max(1, n), (double) pn / chunks));
        });
        PHASES.clear();
        return b.toString();
    }
}
