package dev.lull.optsupport.sleep;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test only (-Dsleep.test.coverage=true): per block-entity class, how often it fell asleep, how many ticks it skipped and
 * how often it woke. An exactness run proves something about a class only if the class slept and woke during it - a
 * machine that never slept passes any comparison (tools/mod_exactness.py prints this next to the verdicts).
 */
public final class SleepCoverage {
    private SleepCoverage() {}

    static final boolean ON = Boolean.getBoolean("sleep.test.coverage");
    /** class -> {sleeps entered, ticks skipped, wakes} */
    private static final Map<String, long[]> BY = new TreeMap<>();

    private static long[] of(Object be) {
        return BY.computeIfAbsent(be.getClass().getName(), k -> new long[3]);
    }

    static synchronized void entered(Object be) {
        of(be)[0]++;
    }

    static synchronized void skipped(Object be, long ticks) {
        of(be)[1] += ticks;
    }

    static synchronized void woke(Object be) {
        of(be)[2]++;
    }

    /** one line per class: "[sleep/coverage] class sleeps N skipped N wakes N" */
    public static synchronized String report() {
        StringBuilder sb = new StringBuilder();
        BY.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "[sleep/coverage] %s sleeps %d skipped %d wakes %d%n", k, v[0], v[1], v[2])));
        return sb.toString();
    }
}
