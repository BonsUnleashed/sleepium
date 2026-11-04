package dev.lull;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;

/**
 * The test tools' tick control on 1.20.1, which has no /tick yet (1.20.3 added it): /tick freeze | unfreeze |
 * step N | query, with 1.21's meaning and output, so tools/exactness.py and tools/bench.py run unchanged. Frozen, a
 * level ticks only its chunk source without ticking chunks (forced chunks keep loading); each step runs one whole
 * server tick. The query reads the server's last 100 tick times.
 */
public final class TickControl {
    private TickControl() {}

    private static volatile boolean frozen;
    private static int steps;
    private static boolean stepping;

    /** MinecraftServer.tickServer, at its start (server thread) */
    public static void beginTick() {
        stepping = frozen && steps > 0;
        if (stepping) steps--;
    }

    /** ServerLevel.tick: this tick runs only the chunk source */
    public static boolean levelFrozen() {
        return frozen && !stepping;
    }

    public static void freeze(boolean f) {
        frozen = f;
        if (!f) steps = 0;
    }

    public static void step(int n) {
        steps += n;
    }

    public static boolean frozen() {
        return frozen;
    }

    /** 1.21's /tick query lines (average, then percentiles over the last 100 ticks) */
    public static String[] query(MinecraftServer server) {
        long[] t = server.tickTimes.clone();
        Arrays.sort(t);
        double avg = Arrays.stream(t).average().orElse(0) / 1e6;
        return new String[] {
                String.format(Locale.ROOT, "Average time per tick: %.1fms (Target: 50.0ms)", avg),
                String.format(Locale.ROOT, "Percentiles: P50: %.1fms P95: %.1fms P99: %.1fms, sample: %d", t[t.length / 2] / 1e6,
                        t[(int) (t.length * 0.95)] / 1e6, t[(int) (t.length * 0.99)] / 1e6, t.length)
        };
    }
}
