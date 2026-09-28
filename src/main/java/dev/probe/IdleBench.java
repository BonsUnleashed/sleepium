package dev.probe;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /probe idle <seconds>}: what the server costs while it has nothing to do — process CPU and the Java threads
 * by group over the window, plus the MainReactor's park / wake counters when it is on.
 */
final class IdleBench {
    private static long endNanos = -1, startNanos;
    private static CpuWindow cpu;

    private IdleBench() {}

    static void start(int seconds) {
        cpu = new CpuWindow();
        dev.probe.optsupport.MainReactor.report(1);   // reset the counters
        startNanos = System.nanoTime();
        endNanos = startNanos + seconds * 1_000_000_000L;
    }

    /** server thread, end of every tick */
    static void onTickEnd(MinecraftServer server) {
        if (endNanos < 0 || System.nanoTime() < endNanos) return;
        endNanos = -1;
        double secs = (System.nanoTime() - startNanos) / 1e9;
        String line = "idle " + String.format(java.util.Locale.ROOT, "%.1f s: ", secs) + cpu.report(secs, 8)
                + (dev.probe.optsupport.MainReactor.ENABLED ? " | " + dev.probe.optsupport.MainReactor.report(secs) : "");
        ProbeMod.LOGGER.info("probe: {}", line);
        server.getPlayerList().broadcastSystemMessage(Component.literal("probe: " + line), false);
    }
}
