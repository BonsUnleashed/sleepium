package dev.probe;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * CPU over a measuring window: the whole process (JIT, GC, native threads included) and the Java threads grouped by
 * name (digits → N, so "Worker-Main-12" and "Worker-Main-3" are one group). Threads that die inside the window lose
 * their time; JVM-internal threads (C2, G1) are only in the process figure.
 */
final class CpuWindow {
    private final long process;
    private final Map<Long, Long> threads;
    private final Map<Long, String> names = new HashMap<>();

    CpuWindow() {
        process = processCpu();
        threads = threadCpu(names);
    }

    /** "process X cores; java threads (cores): group a; ..." for the window of `secs` since construction */
    String report(double secs, int limit) {
        long p = processCpu();
        Map<Long, Long> now = threadCpu(names);
        Map<String, Long> by = new TreeMap<>();
        long java = 0;
        for (var e : now.entrySet()) {
            long d = e.getValue() - threads.getOrDefault(e.getKey(), 0L);
            by.merge(names.getOrDefault(e.getKey(), "?"), d, Long::sum);
            java += d;
        }
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "cpu: process %.2f cores (java threads %.2f); by group:",
                p < 0 || process < 0 ? -1.0 : (p - process) / 1e9 / secs, java / 1e9 / secs));
        by.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(limit)
                .forEach(e -> b.append(String.format(Locale.ROOT, " %s %.2f;", e.getKey(), e.getValue() / 1e9 / secs)));
        return b.toString();
    }

    private static Map<Long, Long> threadCpu(Map<Long, String> names) {
        var mx = ManagementFactory.getThreadMXBean();
        Map<Long, Long> m = new HashMap<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            long c = mx.getThreadCpuTime(t.threadId());
            if (c >= 0) {
                m.put(t.threadId(), c);
                // the name at the window start: vanilla renames a worker while it runs a mailbox task
                // (Util.wrapThreadWithTaskName in dev), so a snapshot at the end may say "light" for a pool worker
                names.putIfAbsent(t.threadId(), t.getName().replaceAll("\\d+", "N"));
            }
        }
        return m;
    }

    static long processCpu() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getProcessCpuTime() : -1;
    }
}
