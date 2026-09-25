package dev.probe.optsupport.sleep;

import dev.probe.BeTickStats;
import dev.probe.FieldDiff;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Universal sleep for block entities (prototype, {@code -Dlab.opt.SleepMixin=true}).
 *
 * <p>Per class, once: {@link SleepAnalysis} derives the clock fields and the conditions on them from bytecode.
 * Per block entity, while awake, every tick is observed:
 * <ul>
 *   <li>no world-visible write (BeTickStats.writes);</li>
 *   <li>no random number drawn (level.random seed unchanged);</li>
 *   <li>only clock fields changed, with the same per-tick deltas as the previous tick;</li>
 *   <li>the same world reads as the previous tick.</li>
 * </ul>
 * After {@value #STABLE} such ticks the block entity sleeps until {@code deadline - MARGIN}, where deadline is the
 * first tick at which any clock condition could change its outcome, given the observed rates. It is subscribed to
 * everything it read (block positions, block entities, entity boxes) and to its own position.
 *
 * <p>On wake (deadline or event), the clock fields are fast-forwarded by rate × skipped ticks and the tick runs
 * normally. Being woken early is always safe: a normal tick is exact by definition.
 */
public final class SleepEngine {
    public static final boolean ON = Boolean.getBoolean("lab.opt.SleepMixin");
    static final int STABLE = Integer.getInteger("lab.sleep.stable", 3);
    static final int MARGIN = Integer.getInteger("lab.sleep.margin", 2);
    static final int MIN_SLEEP = Integer.getInteger("lab.sleep.min", 4);
    /** Optional region filter for A/B-in-one-world tests: only x in [minX, maxX) may sleep. */
    static final int MIN_X = Integer.getInteger("lab.sleep.minX", Integer.MIN_VALUE), MAX_X = Integer.getInteger("lab.sleep.maxX", Integer.MAX_VALUE);

    enum State { AWAKE, ASLEEP, NEVER }

    static final class Sleeper {
        final BlockEntity be;
        final SleepAnalysis.Plan plan;
        final FieldDiff.Layout layout;
        final int[] clockIdx;          // index of each plan clock in the layout
        State state = State.AWAKE;
        long[] prevSnap, prevDelta;
        long prevTick = Long.MIN_VALUE, prevReadHash;
        int stable;
        // asleep
        long sleptAt, wakeAt, skippedTicks, noSleepUntil = Long.MIN_VALUE;
        boolean woken;
        String wakeReason;
        long[] rates;
        List<Long> watchedPos = new ArrayList<>();
        List<Long> watchedSections = new ArrayList<>();
        List<AABB> boxes = new ArrayList<>();
        // last observed tick's reads
        List<Long> readPos = new ArrayList<>();
        List<AABB> readBoxes = new ArrayList<>();

        Sleeper(BlockEntity be, SleepAnalysis.Plan plan) {
            this.be = be;
            this.plan = plan;
            this.layout = FieldDiff.layout(be.getClass());
            clockIdx = new int[plan.clocks().size()];
            for (int c = 0; c < clockIdx.length; c++) {
                clockIdx[c] = -1;
                for (int i = 0; i < layout.fields.length; i++) if (layout.fields[i].equals(plan.clocks().get(c))) clockIdx[c] = i;
            }
        }
    }

    private static final Map<Class<?>, SleepAnalysis.Plan> PLANS = new HashMap<>();
    private static final Map<BlockEntity, Sleeper> SLEEPERS = new IdentityHashMap<>();
    private static final Long2ObjectOpenHashMap<List<Sleeper>> BY_POS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<List<Sleeper>> BY_SECTION = new Long2ObjectOpenHashMap<>();

    // current observed tick (server thread)
    private static Sleeper cur;
    private static Thread curThread;
    private static long w0, seed0, readHash;
    private static final List<Long> tickPos = new ArrayList<>();
    private static final List<AABB> tickBoxes = new ArrayList<>();

    // stats
    static long skipped, entered, observedTicks;
    static final Map<String, Long> WAKES = new TreeMap<>();
    static final Map<String, Long> REFUSED = new TreeMap<>();

    private SleepEngine() {}

    // ------------------------------------------------------------------ tick hooks

    /** HEAD of the block-entity tick: true = skip this tick (asleep). Handles waking and fast-forward. */
    public static boolean skip(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return false;
        Sleeper s = SLEEPERS.get(be);
        if (s == null || s.state != State.ASLEEP) return false;
        long now = level.getGameTime();
        if (!s.woken && now < s.wakeAt) {
            skipped++;
            s.skippedTicks++;   // this call site is reached only when vanilla would really tick
            return true;
        }
        wake(s, now, s.woken ? s.wakeReason : "deadline");
        return false;
    }

    private static void wake(Sleeper s, long now, String reason) {
        advance(s);
        unsubscribe(s);
        s.state = State.AWAKE;
        s.woken = false;
        s.stable = 0;
        s.prevTick = Long.MIN_VALUE;   // re-observe from scratch
        WAKES.merge(reason, 1L, Long::sum);
    }

    /** Apply the pending time to the clock fields (rate x skipped ticks); the block entity may keep sleeping. */
    private static void advance(Sleeper s) {
        long k = s.skippedTicks;   // ticks vanilla would have run while we slept
        s.skippedTicks = 0;
        if (k > 0) {
            for (int c = 0; c < s.clockIdx.length; c++) {
                long r = s.rates[c];
                if (r == 0) continue;
                Field f = s.plan.clocks().get(c);
                try {
                    if (f.getType() == int.class) f.setInt(s.be, (int) (f.getInt(s.be) + r * k));
                    else if (f.getType() == long.class) f.setLong(s.be, f.getLong(s.be) + r * k);
                    else if (f.getType() == short.class) f.setShort(s.be, (short) (f.getShort(s.be) + r * k));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /** A save (or any exact read) of a block entity: bring its lazily advanced clocks up to date. */
    public static void materialize(BlockEntity be) {
        Sleeper s = SLEEPERS.get(be);
        if (s != null && s.state == State.ASLEEP && s.skippedTicks > 0) {
            advance(s);
            MATERIALIZED++;
        }
    }

    /** Fields were replaced from NBT: drop the pending time and re-observe. */
    public static void onLoad(BlockEntity be) {
        Sleeper s = SLEEPERS.get(be);
        if (s == null) return;
        if (s.state == State.ASLEEP) {
            s.skippedTicks = 0;
            request(s, "load");
        }
        s.stable = 0;
        s.prevTick = Long.MIN_VALUE;
    }

    /** An open menu reads the fields every tick: keep the block entity awake while it is watched. */
    public static void viewed(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        Sleeper s = SLEEPERS.get(be);
        if (s == null) return;
        s.noSleepUntil = level.getGameTime() + 20;
        if (s.state == State.ASLEEP) {
            advance(s);
            request(s, "viewed");
        }
    }

    static long MATERIALIZED;

    /** Before a normal tick: start observing (reads, writes, random). */
    public static void before(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        Sleeper s = SLEEPERS.get(be);
        if (s == null) {
            SleepAnalysis.Plan plan = PLANS.computeIfAbsent(be.getClass(), c -> {
                SleepAnalysis.Plan p = SleepAnalysis.analyze(be.getClass(), be.getBlockState().getBlock().getClass());
                p = verify(p);
                System.out.println("[probe/sleep] " + be.getClass().getSimpleName() + ": " + (p.eligible() ? "ELIGIBLE " : "no: ") + p.reason()
                        + "  clocks=" + p.clocks().stream().map(Field::getName).toList() + " conds=" + p.conds().stream().map(SleepAnalysis.Cond::text).toList());
                return p;
            });
            s = new Sleeper(be, plan);
            if (!plan.eligible()) s.state = State.NEVER;
            SLEEPERS.put(be, s);
        }
        if (s.state == State.NEVER) return;
        int x = be.getBlockPos().getX();
        if (x < MIN_X || x >= MAX_X) {
            s.state = State.NEVER;
            return;
        }
        cur = s;
        curThread = Thread.currentThread();
        w0 = BeTickStats.writes;
        seed0 = seed(level);
        readHash = 17;
        tickPos.clear();
        tickBoxes.clear();
    }

    private static SleepAnalysis.Plan verify(SleepAnalysis.Plan p) {
        if (!p.eligible()) return p;
        for (Field f : p.clocks()) if (f == null) return new SleepAnalysis.Plan(false, "clock field not accessible", p.clocks(), p.conds());
        for (SleepAnalysis.Cond c : p.conds()) {
            if (c.mod() == 0 && c.clock() == null) return new SleepAnalysis.Plan(false, "condition without clock field", p.clocks(), p.conds());
        }
        return p;
    }

    /** After a normal tick: decide whether the block entity may sleep. */
    public static void after(BlockEntity be) {
        Sleeper s = cur;
        if (s == null || s.be != be) return;
        cur = null;
        curThread = null;
        observedTicks++;
        Level level = be.getLevel();
        long now = level.getGameTime();
        long[] snap = FieldDiff.snapshot(be, s.layout, null);
        boolean consecutive = s.prevTick == now - 1 && s.prevSnap != null;
        boolean clockOnly = BeTickStats.writes == w0 && seed(level) == seed0;
        long[] delta = new long[snap.length];
        if (consecutive && clockOnly) {
            for (int i = 0; i < snap.length; i++) {
                delta[i] = snap[i] - s.prevSnap[i];
                if (delta[i] != 0 && !isClock(s, i)) {
                    clockOnly = false;
                    break;
                }
            }
            if (clockOnly && readHash != s.prevReadHash) clockOnly = false;
        }
        if (consecutive && clockOnly) {
            boolean sameRates = s.prevDelta != null;
            if (sameRates) for (int c : s.clockIdx) if (c < 0 || delta[c] != s.prevDelta[c]) { sameRates = false; break; }
            s.stable = sameRates ? s.stable + 1 : 1;
        } else {
            s.stable = 0;
        }
        s.prevSnap = snap;
        s.prevDelta = delta;
        s.prevTick = now;
        s.prevReadHash = readHash;
        s.readPos = new ArrayList<>(tickPos);
        s.readBoxes = new ArrayList<>(tickBoxes);
        if (s.stable >= STABLE) trySleep(s, now, delta);
    }

    private static boolean isClock(Sleeper s, int layoutIdx) {
        for (int c : s.clockIdx) if (c == layoutIdx) return true;
        return false;
    }

    private static void trySleep(Sleeper s, long now, long[] delta) {
        if (now < s.noSleepUntil) {
            refuse("viewed");
            return;
        }
        long[] rates = new long[s.clockIdx.length];
        for (int c = 0; c < rates.length; c++) rates[c] = delta[s.clockIdx[c]];
        long t = Long.MAX_VALUE;
        try {
            for (SleepAnalysis.Cond cond : s.plan.conds()) t = Math.min(t, ticksUntilChange(s, cond, rates, now));
        } catch (IllegalAccessException e) {
            refuse("reflection");
            return;
        }
        long sleep = t == Long.MAX_VALUE ? Long.MAX_VALUE : t - MARGIN;
        if (sleep < MIN_SLEEP) {
            refuse(t == 0 ? "condition changes now" : "deadline too close");
            return;
        }
        s.rates = rates;
        s.sleptAt = now;
        s.skippedTicks = 0;
        s.wakeAt = sleep == Long.MAX_VALUE ? Long.MAX_VALUE : now + sleep;
        s.state = State.ASLEEP;
        s.woken = false;
        subscribe(s);
        entered++;
    }

    /** Ticks until the relation between clock and threshold (or the game time residue) can change. */
    private static long ticksUntilChange(Sleeper s, SleepAnalysis.Cond c, long[] rates, long now) throws IllegalAccessException {
        if (c.mod() > 0) {
            long r = Math.floorMod(c.thresholdConst() - (now + 1), c.mod());   // ticks from the next tick
            return r + 1;
        }
        long v = value(c.clock(), s.be), rv = rateOf(s, c.clock(), rates);
        long h = c.thresholdField() != null ? value(c.thresholdField(), s.be) : c.thresholdConst();
        long rh = c.thresholdField() != null ? rateOf(s, c.thresholdField(), rates) : 0;
        long d = v - h, rd = rv - rh;
        if (rd == 0) return Long.MAX_VALUE;          // relation never changes while asleep
        if (d == 0) return 0;                          // equal now, changes next tick
        if (Long.signum(d) == Long.signum(rd)) return Long.MAX_VALUE;   // moving away
        return (Math.abs(d) + Math.abs(rd) - 1) / Math.abs(rd);
    }

    private static long rateOf(Sleeper s, Field f, long[] rates) {
        for (int c = 0; c < s.clockIdx.length; c++) if (s.plan.clocks().get(c).equals(f)) return rates[c];
        return 0;
    }

    private static long value(Field f, Object o) throws IllegalAccessException {
        Class<?> t = f.getType();
        if (t == int.class) return f.getInt(o);
        if (t == long.class) return f.getLong(o);
        if (t == short.class) return f.getShort(o);
        if (t == byte.class) return f.getByte(o);
        throw new IllegalAccessException("non-integral clock " + f);
    }

    private static void refuse(String why) {
        REFUSED.merge(why, 1L, Long::sum);
    }

    // ------------------------------------------------------------------ reads (from ReadProbeMixin)

    public static void readPos(char kind, BlockPos pos) {
        if (cur == null || Thread.currentThread() != curThread) return;
        long p = pos.asLong();
        tickPos.add(p);
        readHash = readHash * 1_000_003L + kind * 31L + p;
    }

    public static void readBox(char kind, AABB box) {
        if (cur == null || Thread.currentThread() != curThread) return;
        tickBoxes.add(box);
        readHash = readHash * 1_000_003L + kind * 31L + Double.doubleToLongBits(box.minX) * 7 + Double.doubleToLongBits(box.minY) * 13
                + Double.doubleToLongBits(box.minZ) * 17 + Double.doubleToLongBits(box.maxX + box.maxY + box.maxZ);
    }

    // ------------------------------------------------------------------ subscriptions and wake events

    private static void subscribe(Sleeper s) {
        s.watchedPos.clear();
        s.watchedSections.clear();
        s.boxes.clear();
        watchPos(s, s.be.getBlockPos().asLong());
        for (long p : s.readPos) watchPos(s, p);
        for (AABB b : s.readBoxes) {
            s.boxes.add(b);
            int x0 = SectionPos.posToSectionCoord(b.minX), x1 = SectionPos.posToSectionCoord(b.maxX);
            int y0 = SectionPos.posToSectionCoord(b.minY), y1 = SectionPos.posToSectionCoord(b.maxY);
            int z0 = SectionPos.posToSectionCoord(b.minZ), z1 = SectionPos.posToSectionCoord(b.maxZ);
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                long key = SectionPos.asLong(x, y, z);
                if (!s.watchedSections.contains(key)) {
                    s.watchedSections.add(key);
                    BY_SECTION.computeIfAbsent(key, k -> new ArrayList<>(2)).add(s);
                }
            }
        }
    }

    private static void watchPos(Sleeper s, long p) {
        if (s.watchedPos.contains(p)) return;
        s.watchedPos.add(p);
        BY_POS.computeIfAbsent(p, k -> new ArrayList<>(2)).add(s);
    }

    private static void unsubscribe(Sleeper s) {
        for (long p : s.watchedPos) {
            List<Sleeper> l = BY_POS.get(p);
            if (l != null && l.remove(s) && l.isEmpty()) BY_POS.remove(p);
        }
        for (long k : s.watchedSections) {
            List<Sleeper> l = BY_SECTION.get(k);
            if (l != null && l.remove(s) && l.isEmpty()) BY_SECTION.remove(k);
        }
        s.watchedPos.clear();
        s.watchedSections.clear();
        s.boxes.clear();
    }

    private static void request(Sleeper s, String reason) {
        if (s.state == State.ASLEEP && !s.woken) {
            s.woken = true;
            s.wakeReason = reason;
        }
    }

    /** A block changed, a block entity was marked changed, or a container slot was set at this position. */
    public static void onPos(Level level, BlockPos pos, String reason) {
        if (BY_POS.isEmpty() || level == null || level.isClientSide) return;
        List<Sleeper> l = BY_POS.get(pos.asLong());
        if (l == null) return;
        for (Sleeper s : List.copyOf(l)) if (s.be.getLevel() == level) request(s, reason);
    }

    /** An entity moved, appeared, changed its item or was removed: wake sleepers whose read boxes it touches. */
    public static void onEntity(Entity e, String reason) {
        if (BY_SECTION.isEmpty()) return;
        Level level = e.level();
        if (level == null || level.isClientSide) return;
        AABB bb = e.getBoundingBox();
        int x0 = SectionPos.posToSectionCoord(bb.minX), x1 = SectionPos.posToSectionCoord(bb.maxX);
        int y0 = SectionPos.posToSectionCoord(bb.minY), y1 = SectionPos.posToSectionCoord(bb.maxY);
        int z0 = SectionPos.posToSectionCoord(bb.minZ), z1 = SectionPos.posToSectionCoord(bb.maxZ);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            List<Sleeper> l = BY_SECTION.get(SectionPos.asLong(x, y, z));
            if (l == null) continue;
            for (Sleeper s : List.copyOf(l)) {
                if (s.be.getLevel() != level) continue;
                for (AABB b : s.boxes) if (b.intersects(bb)) { request(s, reason); break; }
            }
        }
    }

    // ------------------------------------------------------------------ random

    private static Field seedField;
    private static boolean seedTried;

    private static long seed(Level level) {
        if (!seedTried) {
            seedTried = true;
            try {
                Field f = level.random.getClass().getDeclaredField("seed");
                f.setAccessible(true);
                seedField = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                System.out.println("[probe/sleep] level.random seed not readable (" + level.random.getClass().getName() + "): " + e);
            }
        }
        if (seedField == null) return 0;
        try {
            Object v = seedField.get(level.random);
            return v instanceof AtomicLong a ? a.get() : v instanceof Long l ? l : 0;
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ report

    public static String report() {
        Map<String, int[]> byType = new TreeMap<>();
        int asleep = 0;
        for (Sleeper s : SLEEPERS.values()) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(s.be.getType());
            int[] v = byType.computeIfAbsent(id == null ? "?" : id.toString(), k -> new int[3]);
            v[0]++;
            if (s.state == State.ASLEEP) { v[1]++; asleep++; }
            if (s.state == State.NEVER) v[2]++;
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "sleep engine: %s; tracked %d, asleep now %d; skipped ticks %d, sleeps entered %d, observed ticks %d, materialized %d%n",
                ON ? "ON" : "OFF", SLEEPERS.size(), asleep, skipped, entered, observedTicks, MATERIALIZED));
        byType.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "  %-34s tracked %6d  asleep %6d  never %6d%n", k, v[0], v[1], v[2])));
        sb.append("  wakes: ").append(WAKES).append(System.lineSeparator());
        sb.append("  refused: ").append(REFUSED).append(System.lineSeparator());
        SLEEPERS.values().removeIf(s -> s.be.isRemoved() && s.state != State.ASLEEP);
        return sb.toString();
    }
}
