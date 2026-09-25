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
    public static final boolean ON = Boolean.getBoolean("probe.beStats") || Boolean.getBoolean("probe.beFields");
    /** Also diff every field of the block entity before/after its tick (FieldDiff). */
    public static final boolean FIELDS = Boolean.getBoolean("probe.beFields");
    static final int STREAK = 20;

    /** World-visible writes, bumped by the write hooks (server thread). */
    public static long writes;

    private static final class Stat {
        long ticks, idle, sleepable, nanos;
        long memIdle;          // idle AND no field changed in memory
        long clockOnly;        // idle AND every changed field is clock-like (timestamp or +-1 counter)
        long[] asTimestamp, asCounter;   // per field: changes that looked like "= game time" / "+-1 per tick"
        long[] fieldChanges;   // per field of the layout: ticks in which it changed
        FieldDiff.Layout layout;
    }

    private static final Map<BlockEntityType<?>, Stat> STATS = new HashMap<>();
    private static final Map<BlockEntity, int[]> STREAKS = new IdentityHashMap<>();
    private static long t0, w0, lastTick;
    private static BlockEntity current;
    private static FieldDiff.Layout curLayout;
    private static long[] snapA, snapB;

    private BeTickStats() {}

    public static void before(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide) return;
        current = be;
        if (FIELDS) {
            curLayout = FieldDiff.layout(be.getClass());
            snapA = FieldDiff.snapshot(be, curLayout, snapA);
        }
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
        boolean memChanged = false;
        if (FIELDS) {
            FieldDiff.Layout l = curLayout;
            snapB = FieldDiff.snapshot(be, l, snapB);
            if (s.layout == null) {
                s.layout = l;
                s.fieldChanges = new long[l.fields.length];
                s.asTimestamp = new long[l.fields.length];
                s.asCounter = new long[l.fields.length];
            }
            long now = be.getLevel().getGameTime();
            boolean allClock = true;
            for (int i = 0; i < l.fields.length; i++) {
                if (snapA[i] != snapB[i]) {
                    s.fieldChanges[i]++;
                    memChanged = true;
                    Class<?> t = l.fields[i].getType();
                    boolean integral = t == int.class || t == long.class || t == short.class;
                    long d = snapB[i] - snapA[i];
                    if (integral && snapB[i] == now) s.asTimestamp[i]++;
                    else if (integral && (d == 1 || d == -1)) s.asCounter[i]++;
                    else allClock = false;
                }
            }
            if (memChanged && allClock && writes == w0) s.clockOnly++;
        }
        if (writes == w0 && !memChanged) s.memIdle++;
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
            if (FIELDS && s.layout != null) {
                sb.append(String.format(Locale.ROOT, "      truly static (no write, no field change): %.1f%%; only clock fields changed (time passing): %.1f%%%n",
                        100.0 * s.memIdle / Math.max(1, s.ticks), 100.0 * s.clockOnly / Math.max(1, s.ticks)));
                boolean any = false;
                for (int i = 0; i < s.fieldChanges.length; i++) {
                    if (s.fieldChanges[i] == 0) continue;
                    any = true;
                    long c = s.fieldChanges[i];
                    String kind = s.asTimestamp[i] == c ? "timestamp (= game time)" : s.asCounter[i] == c ? "counter (+-1 per tick)"
                            : s.asTimestamp[i] + s.asCounter[i] > 0 ? String.format(Locale.ROOT, "mixed: %.0f%% clock-like", 100.0 * (s.asTimestamp[i] + s.asCounter[i]) / c)
                            : "other";
                    sb.append(String.format(Locale.ROOT, "        %-44s %6.1f%% of ticks  %s%n", s.layout.names[i], 100.0 * c / s.ticks, kind));
                }
                if (!any) sb.append("        (none)%n".formatted());
            }
            allTicks += s.ticks;
            allIdle += s.idle;
            allSleep += s.sleepable;
            allNanos += s.nanos;
        }
        sb.append(String.format(Locale.ROOT, "  %-34s %7d %10.1f %10.1f %9.0f %6.1f%% %9.1f%%%n", "ALL", STREAKS.size(), allTicks / (double) dt,
                allNanos / 1e3 / dt, allNanos / (double) Math.max(1, allTicks), 100.0 * allIdle / Math.max(1, allTicks),
                100.0 * allSleep / Math.max(1, allTicks)));
        if (FIELDS && FieldDiff.failure != null) sb.append("  reflection failure (first): ").append(FieldDiff.failure).append(System.lineSeparator());
        STATS.clear();
        STREAKS.entrySet().removeIf(en -> en.getKey().isRemoved());
        return sb.toString();
    }
}
