package dev.lull;

import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Per-tick timing of the server thread, recorded into preallocated rings (no allocation per tick).
 * <ul>
 *   <li>duration – ServerTickEvent.Pre → Post (roughly MinecraftServer.tickServer)</li>
 *   <li>interval – Pre → next Pre (should be 50 ms; the spread is the scheduling jitter)</li>
 *   <li>alloc    – bytes allocated by the server thread during the tick</li>
 * </ul>
 * Pre is registered at HIGHEST and Post at LOWEST so other mods' tick handlers fall inside the window.
 */
final class TickTimer {
    static final TickTimer INSTANCE = new TickTimer();

    /** One hour at 20 TPS. */
    private static final int CAPACITY = 72_000;
    /** An interval longer than this means the server was paused, not slow. */
    private static final long GAP_NANOS = 5_000_000_000L;

    private final long[] durations = new long[CAPACITY];
    private final long[] intervals = new long[CAPACITY];
    private final long[] allocs = new long[CAPACITY];
    private int count;       // ticks recorded since reset (may exceed CAPACITY; ring wraps)
    private int intervalCount;

    private long tickStart;
    private long lastPre;
    private long allocStart;
    private long startedAtNanos = System.nanoTime();

    private final Map<String, LevelStats> levels = new LinkedHashMap<>();

    static final class LevelStats {
        long start, sum, max, ticks;
    }

    private TickTimer() {}

    void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class, e -> onPre());
        bus.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> {
            onPost();
            SleepCommand.onTickEnd(e.getServer());
        });
        bus.addListener(EventPriority.HIGHEST, LevelTickEvent.Pre.class, e -> onLevel(e.getLevel(), true));
        bus.addListener(EventPriority.LOWEST, LevelTickEvent.Post.class, e -> onLevel(e.getLevel(), false));
    }

    private void onPre() {
        long now = System.nanoTime();
        EventCounter.INSTANCE.onServerTick(Thread.currentThread());
        if (lastPre != 0) {
            long interval = now - lastPre;
            if (interval < GAP_NANOS) {
                intervals[intervalCount++ % CAPACITY] = interval;
            }
        }
        lastPre = now;
        tickStart = now;
        allocStart = ThreadAlloc.current();
    }

    private void onPost() {
        long now = System.nanoTime();
        int slot = count++ % CAPACITY;
        durations[slot] = now - tickStart;
        allocs[slot] = ThreadAlloc.supported() ? ThreadAlloc.current() - allocStart : 0L;
    }

    private void onLevel(Level level, boolean pre) {
        if (level.isClientSide()) {
            return;
        }
        LevelStats s = levels.computeIfAbsent(level.dimension().location().toString(), k -> new LevelStats());
        long now = System.nanoTime();
        if (pre) {
            s.start = now;
        } else if (s.start != 0) {
            long d = now - s.start;
            s.sum += d;
            s.max = Math.max(s.max, d);
            s.ticks++;
        }
    }

    /** Called from the server thread (command), same thread that writes, so no synchronisation needed. */
    void reset() {
        count = 0;
        intervalCount = 0;
        lastPre = 0;
        levels.clear();
        startedAtNanos = System.nanoTime();
    }

    int ticks() {
        return count;
    }

    double windowSeconds() {
        return (System.nanoTime() - startedAtNanos) / 1e9;
    }

    Stats durationStats() {
        return Stats.of(durations, Math.min(count, CAPACITY));
    }

    Stats intervalStats() {
        return Stats.of(intervals, Math.min(intervalCount, CAPACITY));
    }

    Stats allocStats() {
        return Stats.of(allocs, Math.min(count, CAPACITY));
    }

    Map<String, LevelStats> levels() {
        return levels;
    }

    /** Percentiles over a copy of the ring (sorting a copy keeps the ring intact). */
    record Stats(int n, double mean, long min, long p50, long p90, long p99, long p999, long max) {
        static Stats of(long[] ring, int n) {
            if (n == 0) {
                return new Stats(0, 0, 0, 0, 0, 0, 0, 0);
            }
            long[] s = Arrays.copyOf(ring, n);
            Arrays.sort(s);
            double sum = 0;
            for (long v : s) {
                sum += v;
            }
            return new Stats(n, sum / n, s[0], pct(s, 0.50), pct(s, 0.90), pct(s, 0.99), pct(s, 0.999), s[n - 1]);
        }

        private static long pct(long[] sorted, double p) {
            return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
        }

        String asMillis() {
            return String.format(Locale.ROOT, "n=%d mean=%.3f min=%.3f p50=%.3f p90=%.3f p99=%.3f p99.9=%.3f max=%.3f ms",
                    n, mean / 1e6, min / 1e6, p50 / 1e6, p90 / 1e6, p99 / 1e6, p999 / 1e6, max / 1e6);
        }

        String asKiB() {
            return String.format(Locale.ROOT, "n=%d mean=%.1f p50=%.1f p99=%.1f max=%.1f KiB/tick",
                    n, mean / 1024, p50 / 1024.0, p99 / 1024.0, max / 1024.0);
        }
    }
}
