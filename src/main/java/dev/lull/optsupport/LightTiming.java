package dev.lull.optsupport;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;

/** Measurement only (-Dlab.lighttime=true, LightTimingMixin): time in LevelLightEngine.runLightUpdates per thread group. */
public final class LightTiming {
    private LightTiming() {}

    public static final boolean ON = Boolean.getBoolean("lab.lighttime");
    private static final ConcurrentHashMap<String, AtomicLongArray> BY_THREAD = new ConcurrentHashMap<>();

    public static void add(long nanos) {
        String g = Thread.currentThread().getName().replaceAll("\\d+", "N");
        AtomicLongArray a = BY_THREAD.computeIfAbsent(g, k -> new AtomicLongArray(2));
        a.addAndGet(0, nanos);
        a.incrementAndGet(1);
    }

    /** the measured window starts */
    public static void reset() {
        BY_THREAD.clear();
    }

    public static String report() {
        StringBuilder b = new StringBuilder("light engine time in the window:");
        for (Map.Entry<String, AtomicLongArray> e : new TreeMap<>(BY_THREAD).entrySet()) {
            long ns = e.getValue().get(0), n = e.getValue().get(1);
            b.append(String.format(java.util.Locale.ROOT, " %s %d runs %.1f ms (%.1f us per run);", e.getKey(), n, ns / 1e6, n == 0 ? 0 : ns / 1e3 / n));
        }
        return b.toString();
    }
}
