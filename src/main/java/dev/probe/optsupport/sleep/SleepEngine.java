package dev.probe.optsupport.sleep;

import dev.probe.BeTickStats;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Universal sleep for block entities ({@code -Dlab.opt.SleepMixin=true}).
 *
 * <p>Per class, once: {@link SleepAnalysis} derives from bytecode the clock fields, the conditions on them and the
 * only fields a tick can change ({@code watch}); {@link SnapCompiler} compiles a GETFIELD snapshot of those fields.
 * Per block entity (state stored in the object itself, {@link SleeperHolder}), while awake, a tick counts as
 * <i>time passing</i> when: no world-visible write, no random number drawn, the same world reads as the previous
 * tick, and only clock fields changed, with the previous tick's rates. After {@value #STABLE} such ticks it sleeps
 * until {@code deadline - MARGIN} (the first tick any clock condition could change), subscribed to everything it
 * read. On wake the clocks are fast-forwarded by rate × skipped ticks and the tick runs normally; after an early wake
 * one verifying tick with the same rates and reads is enough to sleep again. Block entities that keep failing to
 * sleep are observed exponentially less often (up to every {@value #MAX_BACKOFF} ticks).
 */
public final class SleepEngine {
    public static final boolean ON = Boolean.getBoolean("lab.opt.SleepMixin");
    static final int STABLE = Integer.getInteger("lab.sleep.stable", 3);
    static final int MARGIN = Integer.getInteger("lab.sleep.margin", 2);
    static final int MIN_SLEEP = Integer.getInteger("lab.sleep.min", 4);
    static final int MAX_BACKOFF = Integer.getInteger("lab.sleep.backoff", 64);
    /** A block entity must have ticked this many times before the engine tracks it. */
    static final int MIN_AGE = Integer.getInteger("lab.sleep.minAge", 40);
    /** verifyAsleep on every skipped tick (external field changes); off only for measurements */
    static final boolean VERIFY = !Boolean.getBoolean("lab.sleep.noVerify");
    /** Optional region filter for A/B-in-one-world tests: only x in [minX, maxX) may sleep. */
    static final int MIN_X = Integer.getInteger("lab.sleep.minX", Integer.MIN_VALUE), MAX_X = Integer.getInteger("lab.sleep.maxX", Integer.MAX_VALUE);

    enum State { AWAKE, ASLEEP, NEVER }

    /** Per class: plan + compiled snapshot. */
    static final class ClassInfo {
        final SleepAnalysis.Plan plan;
        final SnapCompiler.Snapper snapper;
        final int[] clockSlot;       // position of each plan clock in plan.watch
        final boolean[] isClock;     // per watch slot

        ClassInfo(SleepAnalysis.Plan plan) {
            this.plan = plan;
            List<Field> w = plan.watch();
            this.snapper = plan.eligible() ? SnapCompiler.compile(w) : null;
            clockSlot = new int[plan.clocks().size()];
            isClock = new boolean[w.size()];
            for (int c = 0; c < clockSlot.length; c++) {
                clockSlot[c] = w.indexOf(plan.clocks().get(c));
                if (clockSlot[c] >= 0) isClock[clockSlot[c]] = true;
            }
        }
    }

    /** deadlines in the lab's time queue (key = Sleeper.id), polled at the start of each level's block-entity tick */
    private static final dev.probe.optsupport.LabScheduler DEADLINES = new dev.probe.optsupport.LabScheduler();
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Sleeper> SCHEDULED = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static long nextId;
    static long deadlineWakes;

    static final class Sleeper {
        final long id = nextId++;
        final BlockEntity be;
        final ClassInfo info;
        State state = State.AWAKE;
        long[] prev, cur;                        // two snapshot buffers, swapped every observed tick
        Object[] refs;                           // asleep: the referenced objects of the reference slots, for verifyAsleep
        long[] rates, prevRates;
        boolean prevValid;
        long prevTick = Long.MIN_VALUE, prevReadHash;
        int stable, failStreak;
        long observeFrom = Long.MIN_VALUE, noSleepUntil = Long.MIN_VALUE;
        // asleep
        long wakeAt, sleepReadHash;
        boolean woken, resleep;
        String wakeReason;
        final LongArrayList watchedPos = new LongArrayList(), watchedSections = new LongArrayList();
        final List<AABB> boxes = new ArrayList<>(2);
        // reads of the current / last observed tick (swapped, no copies)
        LongArrayList readPos = new LongArrayList(), lastReadPos = new LongArrayList();
        List<AABB> readBoxes = new ArrayList<>(2), lastReadBoxes = new ArrayList<>(2);

        Sleeper(BlockEntity be, ClassInfo info) {
            this.be = be;
            this.info = info;
            int n = info.plan.watch().size();
            prev = new long[n];
            cur = new long[n];
            refs = new Object[n];
            rates = new long[info.clockSlot.length];
            prevRates = new long[info.clockSlot.length];
        }
    }

    private static final Map<Class<?>, ClassInfo> CLASSES = new HashMap<>();
    private static final List<Sleeper> ALL = new ArrayList<>();
    private static final Long2ObjectOpenHashMap<List<Sleeper>> BY_POS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<List<Sleeper>> BY_SECTION = new Long2ObjectOpenHashMap<>();

    // the tick being observed (server thread)
    private static Sleeper obs;
    private static Thread obsThread;
    private static long w0, seed0, readHash;
    /** Reads of world state no subscription covers (fluids, light, weather, time of day, players, ...), counted by SleepProbeUnobservedMixin */
    static long unobserved, u0;
    static long verifiedSkips, externalWakes;

    /** Start of a level's block-entity tick: wake every sleeper whose deadline has come (stale entries skipped). */
    public static void pollDeadlines(Level level) {
        if (level.isClientSide || DEADLINES.size() == 0) return;
        long now = level.getGameTime();
        for (long id; (id = DEADLINES.takeDue(now)) != dev.probe.optsupport.LabScheduler.NONE; ) {
            Sleeper s = SCHEDULED.get(id);
            if (s == null) continue;
            if (s.state != State.ASLEEP) {
                SCHEDULED.remove(id);
            } else if (!s.woken && s.wakeAt <= now) {
                SCHEDULED.remove(id);
                deadlineWakes++;
                request(s, "deadline");
            }
        }
        DEADLINES.endTick(now);
    }

    /** From SleepProbeUnobservedMixin: the observed tick read something a sleeping block entity could not be woken by. */
    public static void unobservedRead() {
        if (obs != null && Thread.currentThread() == obsThread) unobserved++;
    }

    /**
     * Before every skipped tick: the watched fields must still equal the snapshot taken when it fell asleep.
     * Anything changed from outside (a bell hit, a menu, another mod calling a setter) wakes it for this very tick.
     */
    public static boolean verifyAsleep(BlockEntity be) {
        if (!VERIFY) return true;
        Sleeper s = sleeper(be);
        if (s == null || s.state != State.ASLEEP) return true;
        // only the block entity's own fields (the cache lines the skip reads anyway): primitives by value, references by
        // identity. Changes inside referenced containers arrive as events (container hooks, setChanged, block updates).
        if (s.info.snapper.same(be, s.prev, s.refs)) {
            verifiedSkips++;
            return true;
        }
        externalWakes++;
        request(s, "external change");
        return false;
    }

    // stats
    static long skipped, entered, resleeps, observedTicks, backedOff, materialized;
    /** ticks skipped by the fast path at the head of BoundTickingBlockEntity.tick (SleepProbeFastSkipMixin) */
    public static long fastSkipped;
    static final Map<String, Long> WAKES = new TreeMap<>();
    static final Map<String, Long> REFUSED = new TreeMap<>();

    private SleepEngine() {}

    private static Sleeper sleeper(BlockEntity be) {
        return (Sleeper) ((SleeperHolder) be).probe$sleeper();
    }

    private static SleeperHolder h(Sleeper s) {
        return (SleeperHolder) s.be;
    }

    // ------------------------------------------------------------------ tick hooks

    /** At the ticker call site (reached only when vanilla really ticks): true = skip this tick. */
    public static boolean skip(BlockEntity be) {
        Sleeper s = sleeper(be);
        if (s == null || s.state != State.ASLEEP) return false;
        Level level = be.getLevel();
        long now = level.getGameTime();
        if (!s.woken && now < s.wakeAt && verifyAsleep(be)) {
            skipped++;
            h(s).probe$setSkipped(h(s).probe$skipped() + 1);
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
        h(s).probe$setWakeAt(Long.MIN_VALUE);
        java.util.Arrays.fill(s.refs, null);   // do not keep replaced objects alive
        // the snapshot taken now is the "before" of this tick: one verifying tick may put it back to sleep
        s.info.snapper.snap(s.be, s.prev);
        s.prevValid = true;
        s.prevTick = now - 1;
        s.prevReadHash = s.sleepReadHash;
        System.arraycopy(s.rates, 0, s.prevRates, 0, s.rates.length);
        s.resleep = true;
        s.stable = 0;
        s.observeFrom = Long.MIN_VALUE;
        WAKES.merge(reason, 1L, Long::sum);
    }

    /** Apply the pending time to the clock fields (rate x skipped ticks); the block entity may keep sleeping. */
    private static void advance(Sleeper s) {
        long k = h(s).probe$skipped();
        h(s).probe$setSkipped(0);
        if (k <= 0) return;
        List<Field> clocks = s.info.plan.clocks();
        for (int c = 0; c < clocks.size(); c++) {
            long r = s.rates[c];
            if (r == 0) continue;
            Field f = clocks.get(c);
            try {
                if (f.getType() == int.class) f.setInt(s.be, (int) (f.getInt(s.be) + r * k));
                else if (f.getType() == long.class) f.setLong(s.be, f.getLong(s.be) + r * k);
                else if (f.getType() == short.class) f.setShort(s.be, (short) (f.getShort(s.be) + r * k));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Before a normal tick: start observing (reads, writes, random), unless backed off. */
    public static void before(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        Sleeper s = sleeper(be);
        if (s == null) {
            // short-lived block entities (moving pistons: thousands, a few ticks each) never get a Sleeper
            SleeperHolder h = (SleeperHolder) be;
            int age = h.probe$age() + 1;
            h.probe$setAge(age);
            if (age < MIN_AGE) return;
            s = create(be);
        }
        if (s.state == State.NEVER) return;
        if (level.getGameTime() < s.observeFrom) {
            s.prevValid = false;   // not observed: the next observation starts a fresh sequence
            return;
        }
        obs = s;
        obsThread = Thread.currentThread();
        w0 = BeTickStats.writes;
        seed0 = seed(level);
        u0 = unobserved;
        readHash = 17;
        s.readPos.clear();
        s.readBoxes.clear();
    }

    private static Sleeper create(BlockEntity be) {
        ClassInfo info = CLASSES.computeIfAbsent(be.getClass(), c -> {
            SleepAnalysis.Plan p = SleepAnalysis.analyze(be.getClass(), be.getBlockState().getBlock().getClass());
            if (p.eligible() && (p.clocks().stream().anyMatch(java.util.Objects::isNull) || p.conds().stream().anyMatch(x -> x.mod() == 0 && x.clock() == null)))
                p = new SleepAnalysis.Plan(false, "inaccessible clock", p.clocks(), p.conds());
            ClassInfo ci = new ClassInfo(p);
            System.out.println("[probe/sleep] " + be.getClass().getSimpleName() + ": " + (p.eligible() ? "ELIGIBLE " : "no: ") + p.reason()
                    + "  clocks=" + p.clocks().stream().map(f -> f == null ? "?" : f.getName()).toList()
                    + " conds=" + p.conds().stream().map(SleepAnalysis.Cond::text).toList()
                    + (p.eligible() ? " watch=" + p.watch().stream().map(Field::getName).toList() + (SnapCompiler.failure != null ? " SNAP FALLBACK: " + SnapCompiler.failure : " (compiled snapshot)") : ""));
            return ci;
        });
        Sleeper s = new Sleeper(be, info);
        int x = be.getBlockPos().getX();
        if (!info.plan.eligible() || x < MIN_X || x >= MAX_X) s.state = State.NEVER;
        ((SleeperHolder) be).probe$setSleeper(s);
        ALL.add(s);
        if ((ALL.size() & 4095) == 0) ALL.removeIf(o -> o.be.isRemoved() && o.state != State.ASLEEP);   // bounded bookkeeping
        return s;
    }

    /** After a normal tick: classify it and maybe fall asleep. */
    public static void after(BlockEntity be) {
        Sleeper s = obs;
        if (s == null || s.be != be) return;
        obs = null;
        obsThread = null;
        observedTicks++;
        Level level = be.getLevel();
        long now = level.getGameTime();
        long[] cur = s.cur;
        s.info.snapper.snap(be, cur);
        boolean clockOnly = s.prevValid && s.prevTick == now - 1 && BeTickStats.writes == w0 && seed(level) == seed0
                && readHash == s.prevReadHash && unobserved == u0;
        if (clockOnly) {
            long[] prev = s.prev;
            boolean[] isClock = s.info.isClock;
            for (int i = 0; i < cur.length; i++) {
                if (cur[i] != prev[i] && !isClock[i]) { clockOnly = false; break; }
            }
        }
        boolean sameRates = false;
        if (clockOnly) {
            sameRates = true;
            int[] slots = s.info.clockSlot;
            for (int c = 0; c < slots.length; c++) {
                long r = cur[slots[c]] - s.prev[slots[c]];
                if (r != s.prevRates[c]) sameRates = false;
                s.rates[c] = r;
            }
            s.stable = sameRates ? s.stable + 1 : 1;
        } else {
            s.stable = 0;
        }
        // swap snapshot buffers and read lists: no copies, no allocation
        s.cur = s.prev;
        s.prev = cur;
        s.prevValid = true;
        s.prevTick = now;
        s.prevReadHash = readHash;
        LongArrayList rp = s.lastReadPos;
        s.lastReadPos = s.readPos;
        s.readPos = rp;
        List<AABB> rb = s.lastReadBoxes;
        s.lastReadBoxes = s.readBoxes;
        s.readBoxes = rb;
        System.arraycopy(s.rates, 0, s.prevRates, 0, s.rates.length);

        boolean resleep = s.resleep;
        s.resleep = false;
        if ((resleep && clockOnly && sameRates) || s.stable >= STABLE) {
            if (resleep && clockOnly && sameRates) resleeps++;
            trySleep(s, now);
        } else if (!clockOnly) {
            fail(s, now, 16);   // busy: back off after a streak of busy ticks
        }
    }

    private static void fail(Sleeper s, long now, int threshold) {
        if (++s.failStreak < threshold) return;
        int shift = Math.min(s.failStreak - threshold, 6);
        long wait = Math.min(MAX_BACKOFF, 2L << shift);
        s.observeFrom = now + wait;
        s.prevValid = false;
        backedOff++;
    }

    private static void trySleep(Sleeper s, long now) {
        if (now < s.noSleepUntil) {
            refuse("viewed");
            return;
        }
        long t = Long.MAX_VALUE;
        try {
            for (SleepAnalysis.Cond cond : s.info.plan.conds()) t = Math.min(t, ticksUntilChange(s, cond, now));
        } catch (IllegalAccessException e) {
            refuse("reflection");
            return;
        }
        long sleep = t == Long.MAX_VALUE ? Long.MAX_VALUE : t - MARGIN;
        if (sleep < MIN_SLEEP) {
            refuse(t == 0 ? "condition changes now" : "deadline too close");
            fail(s, now, 1);
            return;
        }
        s.failStreak = 0;
        h(s).probe$setSkipped(0);
        s.wakeAt = sleep == Long.MAX_VALUE ? Long.MAX_VALUE : now + sleep;
        // the deadline lives in the time queue; the fast skip only checks the woken flag (wakeAt MIN_VALUE)
        if (s.wakeAt != Long.MAX_VALUE) {
            DEADLINES.add(s.wakeAt, s.id);
            SCHEDULED.put(s.id, s);
        }
        h(s).probe$setWakeAt(Long.MAX_VALUE);
        s.sleepReadHash = s.prevReadHash;
        s.state = State.ASLEEP;
        s.woken = false;
        s.info.snapper.refs(s.be, s.refs);   // reference for verifyAsleep (s.prev holds the primitive slots)
        subscribe(s);
        entered++;
    }

    /** Ticks until the relation between clock and threshold (or the game time residue) can change. */
    private static long ticksUntilChange(Sleeper s, SleepAnalysis.Cond c, long now) throws IllegalAccessException {
        if (c.clockMod()) return rateOf(s, c.clock()) == 0 ? Long.MAX_VALUE : 1;   // periodic on a moving clock: may change every tick
        if (c.mod() > 0) return Math.floorMod(c.thresholdConst() - (now + 1), c.mod()) + 1;
        long v = value(c.clock(), s.be), rv = rateOf(s, c.clock());
        long h = (c.thresholdField() != null ? value(c.thresholdField(), s.be) : 0) + c.thresholdConst();   // field + offset, or constant
        long rh = c.thresholdField() != null ? rateOf(s, c.thresholdField()) : 0;
        long d = v - h, rd = rv - rh;
        if (rd == 0) return Long.MAX_VALUE;
        if (d == 0) return 0;
        if (Long.signum(d) == Long.signum(rd)) return Long.MAX_VALUE;
        return (Math.abs(d) + Math.abs(rd) - 1) / Math.abs(rd);
    }

    private static long rateOf(Sleeper s, Field f) {
        List<Field> clocks = s.info.plan.clocks();
        for (int c = 0; c < clocks.size(); c++) if (clocks.get(c).equals(f)) return s.rates[c];
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

    // ------------------------------------------------------------------ external observation

    /** A save (or any exact read): bring the lazily advanced clocks up to date; the block entity keeps sleeping. */
    public static void materialize(BlockEntity be) {
        Sleeper s = sleeper(be);
        if (s != null && s.state == State.ASLEEP && h(s).probe$skipped() > 0) {
            advance(s);
            s.info.snapper.snap(s.be, s.prev);   // the clocks moved on: this is the new reference for verifyAsleep
            materialized++;
        }
    }

    /** Fields were replaced from NBT: drop the pending time and re-observe. */
    public static void onLoad(BlockEntity be) {
        Sleeper s = sleeper(be);
        if (s == null) return;
        if (s.state == State.ASLEEP) {
            h(s).probe$setSkipped(0);
            request(s, "load");
        }
        s.stable = 0;
        s.prevValid = false;
    }

    /** An open menu reads the fields every tick: keep the block entity awake while it is watched. */
    public static void viewed(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        Sleeper s = sleeper(be);
        if (s == null) return;
        s.noSleepUntil = level.getGameTime() + 20;
        if (s.state == State.ASLEEP) {
            advance(s);
            request(s, "viewed");
        }
    }

    // ------------------------------------------------------------------ reads (from ReadProbeMixin)

    public static void readPos(char kind, BlockPos pos) {
        Sleeper s = obs;
        if (s == null || Thread.currentThread() != obsThread) return;
        long p = pos.asLong();
        s.readPos.add(p);
        readHash = readHash * 1_000_003L + kind * 31L + p;
    }

    public static void readBox(char kind, AABB box) {
        Sleeper s = obs;
        if (s == null || Thread.currentThread() != obsThread) return;
        s.readBoxes.add(box);
        readHash = readHash * 1_000_003L + kind * 31L + Double.doubleToLongBits(box.minX) * 7 + Double.doubleToLongBits(box.minY) * 13
                + Double.doubleToLongBits(box.minZ) * 17 + Double.doubleToLongBits(box.maxX + box.maxY + box.maxZ);
    }

    // ------------------------------------------------------------------ subscriptions and wake events

    private static void subscribe(Sleeper s) {
        watchPos(s, s.be.getBlockPos().asLong());
        LongArrayList rp = s.lastReadPos;
        for (int i = 0; i < rp.size(); i++) watchPos(s, rp.getLong(i));
        for (AABB b : s.lastReadBoxes) {
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
        for (int i = 0; i < s.watchedPos.size(); i++) {
            long p = s.watchedPos.getLong(i);
            List<Sleeper> l = BY_POS.get(p);
            if (l != null && l.remove(s) && l.isEmpty()) BY_POS.remove(p);
        }
        for (int i = 0; i < s.watchedSections.size(); i++) {
            long k = s.watchedSections.getLong(i);
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
            h(s).probe$setWakeAt(Long.MIN_VALUE);   // the fast skip lets it through; skip() then wakes it
        }
    }

    /** A block changed, a block entity was marked changed, or a container slot was set at this position. */
    public static void onPos(Level level, BlockPos pos, String reason) {
        if (BY_POS.isEmpty() || level == null || level.isClientSide) return;
        List<Sleeper> l = BY_POS.get(pos.asLong());
        if (l == null) return;
        for (int i = 0; i < l.size(); i++) {
            Sleeper s = l.get(i);
            if (s.be.getLevel() == level) request(s, reason);
        }
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
            for (int i = 0; i < l.size(); i++) {
                Sleeper s = l.get(i);
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
        for (Sleeper s : ALL) {
            var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(s.be.getType());
            int[] v = byType.computeIfAbsent(id == null ? "?" : id.toString(), k -> new int[3]);
            v[0]++;
            if (s.state == State.ASLEEP) { v[1]++; asleep++; }
            if (s.state == State.NEVER) v[2]++;
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "sleep engine: %s; tracked %d, asleep now %d; skipped ticks %d (fast path %d), sleeps entered %d (re-sleeps %d), observed ticks %d, backed off %d, materialized %d; verified skips %d, external-change wakes %d, unobserved reads %d%n",
                ON ? "ON" : "OFF", ALL.size(), asleep, skipped + fastSkipped, fastSkipped, entered, resleeps, observedTicks, backedOff, materialized,
                verifiedSkips, externalWakes, unobserved));
        sb.append(String.format(Locale.ROOT, "  deadline queue: %d pending, %d added, %d woken by the scheduler%n", DEADLINES.size(), DEADLINES.added, deadlineWakes));
        byType.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "  %-34s tracked %6d  asleep %6d  never %6d%n", k, v[0], v[1], v[2])));
        sb.append("  wakes: ").append(WAKES).append(System.lineSeparator());
        sb.append("  refused: ").append(REFUSED).append(System.lineSeparator());
        ALL.removeIf(s -> s.be.isRemoved() && s.state != State.ASLEEP);
        return sb.toString();
    }
}
