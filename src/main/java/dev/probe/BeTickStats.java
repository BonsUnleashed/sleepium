package dev.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Generic "idle tick" detector for every block-entity type, vanilla or modded ({@code -Dprobe.beStats=true}).
 *
 * <p>Each block-entity tick is wrapped (BeTickMixin, the one call site all types share). A tick is <b>idle</b> when
 * it made no world-visible write: {@link #writes} is bumped by hooks on setBlock, BlockEntity.setChanged, entity
 * add/remove, ItemEntity.setItem, addEffect, block events, GameEvents, sounds and scheduled ticks. A tick is
 * <b>sleepable</b> when it is part of a run of ≥ {@value #STREAK} idle ticks of the same block entity.
 *
 * <p>Limits: changes to a block entity's own fields without setChanged (e.g. a hopper's cooldown counter) are not
 * seen, so "idle" means "nothing the world could observe happened", not "the object did not change".
 */
public final class BeTickStats {
    public static final boolean ON = Boolean.getBoolean("probe.beStats");
    static final int STREAK = 20;

    /** World-visible writes, bumped by the write hooks (server thread). */
    public static long writes;

    private static final class Stat {
        long ticks, idle, sleepable, nanos;
    }

    private static final Map<BlockEntityType<?>, Stat> STATS = new HashMap<>();
    private static final Map<BlockEntity, int[]> STREAKS = new IdentityHashMap<>();
    private static long t0, w0, lastTick;
    private static BlockEntity current;

    private BeTickStats() {}

    public static void before(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return;
        current = be;
        w0 = writes;
        t0 = System.nanoTime();
    }

    public static void after() {
        BlockEntity be = current;
        if (be == null) return;
        long dt = System.nanoTime() - t0;
        current = null;
        Stat s = STATS.computeIfAbsent(be.getType(), k -> new Stat());
        s.ticks++;
        s.nanos += dt;
        int[] streak = STREAKS.computeIfAbsent(be, k -> new int[1]);
        if (writes == w0) {
            s.idle++;
            if (++streak[0] >= STREAK) s.sleepable++;
        } else {
            streak[0] = 0;
        }
    }

    /** Per-type table since the previous call, sorted by time spent. */
    public static String report(MinecraftServer server) {
        long tick = server.getTickCount();
        long dt = Math.max(1, tick - lastTick);
        lastTick = tick;
        if (!ON) return "block-entity stats are off: start with -Dprobe.beStats=true\n";
        Map<BlockEntityType<?>, Integer> instances = new HashMap<>();
        for (BlockEntity be : STREAKS.keySet()) instances.merge(be.getType(), 1, Integer::sum);
        List<Map.Entry<BlockEntityType<?>, Stat>> rows = new ArrayList<>(STATS.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue().nanos, a.getValue().nanos));
        long allTicks = 0, allIdle = 0, allSleep = 0, allNanos = 0;
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "block-entity ticks over %d server ticks (idle = no world-visible write; sleepable = in a run of >= %d idle ticks)%n", dt, STREAK));
        sb.append(String.format(Locale.ROOT, "  %-34s %7s %10s %10s %9s %7s %10s%n", "type", "count", "ticks/t", "us/tick", "ns/each", "idle%", "sleepable%"));
        for (var e : rows) {
            Stat s = e.getValue();
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(e.getKey());
            sb.append(String.format(Locale.ROOT, "  %-34s %7d %10.1f %10.1f %9.0f %6.1f%% %9.1f%%%n",
                    id == null ? "?" : id.toString(), instances.getOrDefault(e.getKey(), 0), s.ticks / (double) dt,
                    s.nanos / 1e3 / dt, s.nanos / (double) Math.max(1, s.ticks),
                    100.0 * s.idle / Math.max(1, s.ticks), 100.0 * s.sleepable / Math.max(1, s.ticks)));
            allTicks += s.ticks;
            allIdle += s.idle;
            allSleep += s.sleepable;
            allNanos += s.nanos;
        }
        sb.append(String.format(Locale.ROOT, "  %-34s %7d %10.1f %10.1f %9.0f %6.1f%% %9.1f%%%n", "ALL", STREAKS.size(), allTicks / (double) dt,
                allNanos / 1e3 / dt, allNanos / (double) Math.max(1, allTicks), 100.0 * allIdle / Math.max(1, allTicks),
                100.0 * allSleep / Math.max(1, allTicks)));
        STATS.clear();
        STREAKS.entrySet().removeIf(en -> en.getKey().isRemoved());
        return sb.toString();
    }
}
