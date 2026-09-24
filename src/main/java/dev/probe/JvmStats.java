package dev.probe;

import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * JVM-side resource accounting for one recording window (start() .. report()).
 * <ul>
 *   <li>per thread GROUP (name with trailing digits stripped): CPU time split into user and kernel
 *       (kernel = cpu - user, from ThreadMXBean). Threads that die inside the window lose their time
 *       (reported as a caveat by the "threads started/ended" counters).</li>
 *   <li>process CPU (all threads incl. GC/JIT native threads) → average cores used</li>
 *   <li>GC collections/time per collector, JIT compilation time, threads started in the window</li>
 * </ul>
 * System-wide user/privileged/interrupt/DPC time and context switches come from typeperf (tools/run_suite.sh).
 */
final class JvmStats {
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean OS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private record ThreadSample(String group, long cpu, long user) {}

    private static Map<Long, ThreadSample> threadsAtStart = Map.of();
    private static long processCpuAtStart, wallAtStart, startedAtStart, jitAtStart;
    private static final Map<String, long[]> gcAtStart = new HashMap<>();

    private JvmStats() {}

    static {
        try {
            if (THREADS.isThreadCpuTimeSupported()) THREADS.setThreadCpuTimeEnabled(true);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static String group(String threadName) {
        return threadName.replaceAll("[-# ]?\\d+$", "-N");
    }

    private static Map<Long, ThreadSample> sampleThreads() {
        long[] ids = THREADS.getAllThreadIds();
        long[] cpu = THREADS.getThreadCpuTime(ids);
        long[] user = THREADS.getThreadUserTime(ids);
        var infos = THREADS.getThreadInfo(ids);
        Map<Long, ThreadSample> m = new HashMap<>(ids.length * 2);
        for (int i = 0; i < ids.length; i++) {
            if (infos[i] != null && cpu[i] >= 0) m.put(ids[i], new ThreadSample(group(infos[i].getThreadName()), cpu[i], user[i]));
        }
        return m;
    }

    static void start() {
        threadsAtStart = sampleThreads();
        processCpuAtStart = OS.getProcessCpuTime();
        wallAtStart = System.nanoTime();
        startedAtStart = THREADS.getTotalStartedThreadCount();
        CompilationMXBean jit = ManagementFactory.getCompilationMXBean();
        jitAtStart = jit != null && jit.isCompilationTimeMonitoringSupported() ? jit.getTotalCompilationTime() : 0;
        gcAtStart.clear();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcAtStart.put(gc.getName(), new long[] {gc.getCollectionCount(), gc.getCollectionTime()});
        }
    }

    /** Summary numbers for the CSV row. */
    record Totals(double cores, double kernelShare, double gcMsPerSec, long gcCount, long jitMs, long threadsStarted) {}

    static Totals totals() {
        double wallS = (System.nanoTime() - wallAtStart) / 1e9;
        double procCpuS = (OS.getProcessCpuTime() - processCpuAtStart) / 1e9;
        long cpu = 0, user = 0;
        for (Map.Entry<Long, ThreadSample> e : sampleThreads().entrySet()) {
            ThreadSample s0 = threadsAtStart.get(e.getKey());
            cpu += e.getValue().cpu() - (s0 == null ? 0 : s0.cpu());
            user += e.getValue().user() - (s0 == null ? 0 : s0.user());
        }
        long gcCount = 0, gcMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long[] s0 = gcAtStart.getOrDefault(gc.getName(), new long[2]);
            gcCount += gc.getCollectionCount() - s0[0];
            gcMs += gc.getCollectionTime() - s0[1];
        }
        CompilationMXBean jit = ManagementFactory.getCompilationMXBean();
        long jitMs = jit != null && jit.isCompilationTimeMonitoringSupported() ? jit.getTotalCompilationTime() - jitAtStart : 0;
        return new Totals(procCpuS / wallS, cpu == 0 ? 0 : (double) (cpu - user) / cpu, gcMs / wallS, gcCount, jitMs,
                THREADS.getTotalStartedThreadCount() - startedAtStart);
    }

    static String report() {
        double wallS = (System.nanoTime() - wallAtStart) / 1e9;
        Map<String, long[]> groups = new TreeMap<>();   // group -> {cpu, user, threads}
        for (Map.Entry<Long, ThreadSample> e : sampleThreads().entrySet()) {
            ThreadSample s0 = threadsAtStart.get(e.getKey());
            long[] g = groups.computeIfAbsent(e.getValue().group(), k -> new long[3]);
            g[0] += e.getValue().cpu() - (s0 == null ? 0 : s0.cpu());
            g[1] += e.getValue().user() - (s0 == null ? 0 : s0.user());
            g[2]++;
        }
        Totals t = totals();
        StringBuilder sb = new StringBuilder("## CPU & JVM (window " + String.format(Locale.ROOT, "%.1f s", wallS) + ")\n");
        sb.append(String.format(Locale.ROOT, "process CPU: %.2f cores avg (all threads incl. GC/JIT)   kernel share of Java-thread CPU: %.1f %%%n",
                t.cores(), 100 * t.kernelShare()));
        sb.append(String.format(Locale.ROOT, "GC: %d collections, %.1f ms/s   JIT: %d ms compiling   threads started in window: %d%n",
                t.gcCount(), t.gcMsPerSec(), t.jitMs(), t.threadsStarted()));
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long[] s0 = gcAtStart.getOrDefault(gc.getName(), new long[2]);
            sb.append(String.format(Locale.ROOT, "  %-28s %6d collections %8d ms%n", gc.getName(),
                    gc.getCollectionCount() - s0[0], gc.getCollectionTime() - s0[1]));
        }
        sb.append(String.format(Locale.ROOT, "%-40s %8s %10s %10s %8s%n", "thread group", "threads", "cores", "user %", "kernel %"));
        groups.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .filter(e -> e.getValue()[0] > 1_000_000)   // > 1 ms of CPU
                .forEach(e -> {
                    long[] g = e.getValue();
                    sb.append(String.format(Locale.ROOT, "%-40s %8d %10.3f %10.1f %8.1f%n", e.getKey(), g[2], g[0] / 1e9 / wallS,
                            100.0 * g[1] / g[0], 100.0 * (g[0] - g[1]) / g[0]));
                });
        return sb.toString();
    }

    static List<String> csvHeader() {
        return List.of("cpu_cores", "kernel_share", "gc_ms_per_s", "gc_count", "jit_ms", "threads_started");
    }

    static String csvRow() {
        Totals t = totals();
        return String.format(Locale.ROOT, "%.3f,%.3f,%.2f,%d,%d,%d", t.cores(), t.kernelShare(), t.gcMsPerSec(), t.gcCount(), t.jitMs(), t.threadsStarted());
    }
}
