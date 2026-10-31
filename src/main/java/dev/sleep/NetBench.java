package dev.sleep;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /probe net <seconds> <label>}: what networking costs over a window — server-thread send time and socket bytes
 * (NetStats, with -Dprobe.net.stats=true), NetReactor batching, MSPT and CPU by thread group (netty IO threads
 * included). One line "probe: net '<label>': ..." in the log.
 */
final class NetBench {
    private static long endNanos = -1, startNanos;
    private static String label;
    private static CpuWindow cpu;
    private static long tickNanos, ticks, maxTick;
    private static long tickStart;

    private NetBench() {}

    static void start(int seconds, String l) {
        label = l;
        cpu = new CpuWindow();
        NetStats.report(1, 0);
        dev.sleep.net.NetReactor.report();
        dev.sleep.net.Bulk.report();
        dev.sleep.net.Coalesce.report();
        tickNanos = ticks = maxTick = 0;
        startNanos = System.nanoTime();
        endNanos = startNanos + seconds * 1_000_000_000L;
    }

    /** server thread, start of every tick (ServerTickEvent.Pre) */
    static void onTickStart() {
        NetStats.serverThread = Thread.currentThread();
        tickStart = System.nanoTime();
    }

    /** server thread, end of every tick */
    static void onTickEnd(MinecraftServer server) {
        NetStats.serverThread = Thread.currentThread();
        if (endNanos < 0) return;
        if (tickStart != 0) {
            long d = System.nanoTime() - tickStart;
            tickNanos += d;
            ticks++;
            maxTick = Math.max(maxTick, d);
        }
        if (System.nanoTime() < endNanos) return;
        endNanos = -1;
        double secs = (System.nanoTime() - startNanos) / 1e9;
        int players = server.getPlayerList().getPlayerCount();
        String line = "net '" + label + "': " + String.format(java.util.Locale.ROOT, "%.1f s, tick mean %.2f ms max %.2f ms; ", secs,
                ticks == 0 ? 0 : tickNanos / 1e6 / ticks, maxTick / 1e6)
                + NetStats.report(secs, players) + " | " + dev.sleep.net.NetReactor.report() + " | " + dev.sleep.net.Bulk.report() + " | " + dev.sleep.net.Coalesce.report() + " | " + cpu.report(secs, 8);
        SleepMod.LOGGER.info("probe: {}", line);
        server.getPlayerList().broadcastSystemMessage(Component.literal("probe: net '" + label + "' done"), false);
    }
}
