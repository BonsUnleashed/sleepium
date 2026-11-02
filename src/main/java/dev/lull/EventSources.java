package dev.lull;

import dev.lull.optsupport.CompositeEventBus;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Counts every kind of "event" the game has, so we can see which ones go through our bus and which never touch it.
 * <ul>
 *   <li>NeoForge game bus posts: counted by CompositeEventBus when {@code -Dlab.opt.busStats=true}</li>
 *   <li>vanilla mechanisms inside the tick (EventSourcesMixin, server levels only):
 *       block events (pistons, note blocks, chest lids), GameEvents (vibrations/sculk), scheduled block/fluid tick
 *       requests, neighbour updates, block-entity ticks (polling), entity ticks</li>
 * </ul>
 * Plain fields: all sources are written on the server thread only.
 */
public final class EventSources {
    public static long blockEvents, gameEvents, scheduledTicks, neighborUpdates, shapeUpdates, blockEntityTicks, entityTicks;
    private static long[] last = new long[7];
    private static long lastTick, lastBus;
    private static long[] lastPaths = new long[6];

    private EventSources() {}

    private static long[] now() {
        return new long[] {blockEvents, gameEvents, scheduledTicks, neighborUpdates, shapeUpdates, blockEntityTicks, entityTicks};
    }

    /** Per-tick rates since the previous call. */
    public static String report(MinecraftServer server) {
        long tick = server.getTickCount();
        long dt = Math.max(1, tick - lastTick);
        long[] n = now();
        String[] names = {"block events (pistons, note blocks, chests)", "GameEvents (vibrations, sculk)",
                "scheduled block/fluid tick requests", "neighbour updates (neighborChanged)", "shape updates (updateShape)",
                "block-entity ticks (polling)", "entity ticks"};
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "events over %d ticks, per tick:%n", dt));
        sb.append("  -- vanilla mechanisms (do NOT go through any event bus)\n");
        for (int i = 0; i < n.length; i++) {
            sb.append(String.format(Locale.ROOT, "  %-46s %10.1f%n", names[i], (n[i] - last[i]) / (double) dt));
        }
        if (NeoForge.EVENT_BUS instanceof CompositeEventBus bus) {
            long[] p = CompositeEventBus.pathCounts();
            long total = 0;
            for (long v : p) total += v;
            sb.append(String.format(Locale.ROOT, "  -- NeoForge.EVENT_BUS = CompositeEventBus: %.1f posts/tick%n", (total - lastBus) / (double) dt));
            String[] pn = CompositeEventBus.PATH_NAMES;
            for (int i = 0; i < p.length; i++) {
                sb.append(String.format(Locale.ROOT, "     %-43s %10.1f%n", pn[i], (p[i] - lastPaths[i]) / (double) dt));
            }
            if (!CompositeEventBus.STATS) sb.append("     (path counters off: -Dlab.opt.busStats=true)\n");
            else sb.append(CompositeEventBus.topTypes(12, dt));
            lastBus = total;
            lastPaths = p;
        } else {
            sb.append("  -- NeoForge.EVENT_BUS is the vanilla bus (" + NeoForge.EVENT_BUS.getClass().getSimpleName()
                    + "): enable -Dlab.opt.NeoForgeBusMixin=true\n");
        }
        sb.append(dev.lull.optsupport.OptCounters.report());
        last = n;
        lastTick = tick;
        return sb.toString();
    }
}
