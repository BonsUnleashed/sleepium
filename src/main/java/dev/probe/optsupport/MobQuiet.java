package dev.probe.optsupport;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.entity.Mob;

/**
 * Measurement only (-Dprobe.mobQuiet=true, MobQuietMixin): how idle mobs are, the ceiling of a mob sleep. Per class:
 * the share of ticks whose visible state (position, rotations, motion, on-ground, the running goals) is the same after
 * the tick as before, the lengths of such quiet streaks, and the random draws per tick on the mob's own random and on
 * any other (shared) random. Printed every 600 game ticks on the server thread, then reset.
 */
public final class MobQuiet {
    public static final boolean ON = Boolean.getBoolean("probe.mobQuiet");
    /** the mob being ticked (server thread), for the draw counter */
    public static Mob current;
    public static int own, shared;

    private static final class Stats {
        long ticks, quiet, quietWithOwnDraws, ownDraws, sharedDraws, ticksWithShared, streaks, streakTicks;
        final long[] hist = new long[8];   // streak lengths: 1, 2-3, 4-7, 8-15, 16-31, 32-63, 64-127, 128+
    }

    private static final Map<String, Stats> STATS = new TreeMap<>();
    private static final Int2IntOpenHashMap STREAK = new Int2IntOpenHashMap();
    private static final Int2LongOpenHashMap BEFORE = new Int2LongOpenHashMap();
    private static long lastPrint = -1;

    private MobQuiet() {}

    public static void before(Mob m) {
        current = m;
        own = shared = 0;
        BEFORE.put(m.getId(), hash(m));
    }

    public static void after(Mob m) {
        current = null;
        Stats s = STATS.computeIfAbsent(m.getClass().getSimpleName(), k -> new Stats());
        boolean quiet = hash(m) == BEFORE.get(m.getId());
        s.ticks++;
        s.ownDraws += own;
        s.sharedDraws += shared;
        if (shared > 0) s.ticksWithShared++;
        int id = m.getId();
        if (quiet) {
            s.quiet++;
            if (own > 0) s.quietWithOwnDraws++;
            STREAK.addTo(id, 1);
        } else {
            int len = STREAK.remove(id);
            if (len > 0) {
                s.streaks++;
                s.streakTicks += len;
                s.hist[Math.min(7, 31 - Integer.numberOfLeadingZeros(len))]++;
            }
        }
        long t = m.level().getGameTime();
        if (t % 600 == 0 && t != lastPrint) {
            lastPrint = t;
            print();
        }
    }

    private static long hash(Mob m) {
        long h = 1469598103934665603L;
        h = mix(h, Double.doubleToLongBits(m.getX()));
        h = mix(h, Double.doubleToLongBits(m.getY()));
        h = mix(h, Double.doubleToLongBits(m.getZ()));
        h = mix(h, Float.floatToIntBits(m.getXRot()));
        h = mix(h, Float.floatToIntBits(m.getYRot()));
        h = mix(h, Float.floatToIntBits(m.getYHeadRot()));
        h = mix(h, Float.floatToIntBits(m.yBodyRot));
        var v = m.getDeltaMovement();
        h = mix(h, Double.doubleToLongBits(v.x));
        h = mix(h, Double.doubleToLongBits(v.y));
        h = mix(h, Double.doubleToLongBits(v.z));
        h = mix(h, m.onGround() ? 1 : 0);
        h = mix(h, m.getNavigation().isDone() ? 1 : 0);
        for (var g : m.goalSelector.getAvailableGoals()) if (g.isRunning()) h = mix(h, System.identityHashCode(g));
        return h;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 1099511628211L;
    }

    private static void print() {
        StringBuilder sb = new StringBuilder("[probe/mobquiet] per class: ticks, quiet %, quiet with own draws %, own draws/tick, shared draws/tick, ticks with shared %, mean quiet streak, streaks 1|2|4|8|16|32|64|128+");
        for (var e : STATS.entrySet()) {
            Stats s = e.getValue();
            if (s.ticks == 0) continue;
            sb.append(String.format(Locale.ROOT, "%n   %-14s %7d  %5.1f %%  %5.1f %%  %5.2f  %5.2f  %5.1f %%  %6.1f   ", e.getKey(), s.ticks,
                    100.0 * s.quiet / s.ticks, 100.0 * s.quietWithOwnDraws / s.ticks, (double) s.ownDraws / s.ticks, (double) s.sharedDraws / s.ticks,
                    100.0 * s.ticksWithShared / s.ticks, s.streaks == 0 ? 0.0 : (double) s.streakTicks / s.streaks));
            for (int i = 0; i < 8; i++) sb.append(i == 0 ? "" : "|").append(s.hist[i]);
        }
        System.out.println(sb);
        STATS.clear();
    }
}
