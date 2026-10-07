package dev.probe.optsupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Measurement (-Dlab.pgen.trace=true): once a server tick has run 30 ms, a daemon thread takes the server thread's
 * stack every 2 ms; at the tick's end the most frequent "where" (the first frames below the JDK) is reported with the
 * slow tick's profiler tree (GenTraceSlowTickMixin). Ticks under 30 ms are never sampled.
 */
public final class TickSampler {
    private TickSampler() {}

    private static volatile Thread main;
    private static volatile long tickStart;
    private static final Map<String, int[]> SAMPLES = new HashMap<>();
    private static Thread sampler;

    public static synchronized void tickStart(Thread t) {
        main = t;
        tickStart = System.nanoTime();
        SAMPLES.clear();
        if (sampler == null) {
            sampler = new Thread(TickSampler::loop, "probe-tick-sampler");
            sampler.setDaemon(true);
            sampler.start();
        }
    }

    private static volatile String last = "";

    /** the work part of the last server tick's samples (read with the slow tick's report) */
    public static String lastTick() {
        String l = last;
        last = "";
        return l;
    }

    /** tickServer's end: the work is over, the wait for the next tick is not sampled */
    public static synchronized void tickEnd() {
        tickStart = 0;
        last = summary();
    }

    private static String summary() {
        if (SAMPLES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n   samples (every 2 ms past 30 ms):");
        SAMPLES.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).limit(4)
                .forEach(e -> sb.append("\n     ").append(e.getValue()[0]).append("x ").append(e.getKey()));
        SAMPLES.clear();
        return sb.toString();
    }

    private static void loop() {
        while (true) {
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            long s = tickStart;
            Thread t = main;
            if (s == 0 || t == null || System.nanoTime() - s < 30_000_000L) continue;
            StackTraceElement[] st = t.getStackTrace();
            StringBuilder where = new StringBuilder();
            int n = 0;
            for (StackTraceElement e : st) {
                String c = e.getClassName();
                if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("it.unimi")) continue;
                if (n > 0) where.append(" < ");
                where.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
                if (++n >= 5) break;
            }
            synchronized (TickSampler.class) {
                if (tickStart == s) SAMPLES.computeIfAbsent(where.toString(), k -> new int[1])[0]++;
            }
        }
    }
}
