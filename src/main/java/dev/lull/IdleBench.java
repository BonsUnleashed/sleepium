package dev.lull;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /sleep idle <seconds>}: what the server costs while it has nothing to do — process CPU and the Java threads
 * by group over the window, plus the MainReactor's park / wake counters when it is on.
 */
final class IdleBench {
    private static long endNanos = -1, startNanos;
    private static CpuWindow cpu;

    private IdleBench() {}

    /** heap in use, the old generation after its last collection (what survives), committed heap, live threads */
    static String memory() {
        var mem = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long oldAfter = -1;
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans())
            if (pool.getName().contains("Old Gen") && pool.getCollectionUsage() != null) oldAfter = pool.getCollectionUsage().getUsed();
        return String.format(java.util.Locale.ROOT, "heap used %d MB of %d MB committed (max %d), old gen after last GC %d MB, %d live threads",
                mem.getUsed() >> 20, mem.getCommitted() >> 20, mem.getMax() >> 20, oldAfter >> 20, java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
    }

    static void start(int seconds) {
        cpu = new CpuWindow();
        dev.lull.optsupport.MainReactor.report(1);   // reset the counters
        startNanos = System.nanoTime();
        endNanos = startNanos + seconds * 1_000_000_000L;
    }

    /** server thread, end of every tick */
    static void onTickEnd(MinecraftServer server) {
        if (endNanos < 0 || System.nanoTime() < endNanos) return;
        endNanos = -1;
        double secs = (System.nanoTime() - startNanos) / 1e9;
        String line = "idle " + String.format(java.util.Locale.ROOT, "%.1f s: ", secs) + cpu.report(secs, 8) + " | " + memory()
                + (dev.lull.optsupport.MainReactor.ENABLED ? " | " + dev.lull.optsupport.MainReactor.report(secs) : "");
        SleepMod.LOGGER.info("sleep: {}", line);
        server.getPlayerList().broadcastSystemMessage(Component.literal("sleep: " + line), false);
    }
}
