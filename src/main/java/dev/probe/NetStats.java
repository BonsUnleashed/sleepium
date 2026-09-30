package dev.probe;

import java.util.concurrent.atomic.LongAdder;

/**
 * Measurement only (-Dprobe.net.stats=true, NetStats* mixins): what sending costs the server thread and what reaches
 * the sockets. sends / sendNanos: Connection.send calls on the server thread and the time inside them (vanilla: a netty
 * task per packet; NetReactor: an append to the batch); frames / bytes: every length-prefixed frame written to a
 * socket, after compression (in-memory singleplayer connections write none).
 */
public final class NetStats {
    public static final boolean ON = Boolean.getBoolean("probe.net.stats");
    public static final LongAdder SENDS = new LongAdder(), SEND_NANOS = new LongAdder(), FRAMES = new LongAdder(), BYTES = new LongAdder();
    /** server thread only */
    public static long sendStart;
    public static int depth;
    public static volatile Thread serverThread;

    private NetStats() {}

    public static String report(double secs, int players) {
        long s = SENDS.sumThenReset(), n = SEND_NANOS.sumThenReset(), f = FRAMES.sumThenReset(), b = BYTES.sumThenReset();
        double ticks = secs * 20;
        return String.format(java.util.Locale.ROOT,
                "net: server thread %d sends (%.1f per tick), %.2f us each, %.3f ms per tick; sockets %d frames, %.1f KB per tick (%.1f KB per tick per player, %d players)",
                s, s / ticks, s == 0 ? 0 : n / 1e3 / s, n / 1e6 / ticks, f, b / 1024.0 / ticks, players == 0 ? 0 : b / 1024.0 / ticks / players, players);
    }
}
