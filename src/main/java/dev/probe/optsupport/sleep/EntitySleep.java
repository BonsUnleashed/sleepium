package dev.probe.optsupport.sleep;

import dev.probe.BeTickStats;
import dev.probe.optsupport.LabScheduler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Entity sleep ({@code -Dlab.opt.EntitySleep=true}): period replay of idle entities, vanilla and modded alike.
 *
 * <p>The class's tick() is analysed once (SleepAnalysis, entity mode): its own state, its clocks (tickCount and
 * counters), the conditions on them and the period L of every "clock % N" test. At run time an entity is observed:
 * a full snapshot of all its fields (vectors, boxes, synched data, random seed by value) before and after each tick.
 * When L consecutive ticks made no world-visible write, drew no shared random number, read nothing unobserved, and
 * the state before tick L equals the state before tick 0 (clocks aside), the tick is a periodic function of state the
 * engine holds: the same inputs give the same L ticks again. The entity then sleeps: each tick the engine checks the
 * state is the expected one for the phase, writes the recorded after-tick values of the fields that vary, and
 * advances the clocks by their rates. It wakes on a block change in the region its ticks read, an entity event in the
 * boxes they read, a clock deadline (LabScheduler), or any unexpected field value. Code not executed during the
 * observed period is not executed while asleep (same state and inputs, same path).
 *
 * <p>{@code -Dlab.entsleep.diag=ItemEntity}: print what the fields of a few entities of that class do per tick.
 */
public final class EntitySleep {
    public static final boolean ON = Boolean.getBoolean("lab.opt.EntitySleep");
    public static final String DIAG = System.getProperty("lab.entsleep.diag");
    private static final boolean VERIFY = !Boolean.getBoolean("lab.entsleep.noVerify");
    private static final int MAX_PERIOD = Integer.getInteger("lab.entsleep.maxPeriod", 64);
    /** clone tests: only entities at x >= minX may sleep */
    private static final int MIN_X = Integer.getInteger("lab.entsleep.minX", Integer.MIN_VALUE);
    private static final int MAX_DIFFS = 8192;
    private static final boolean DEBUG = Boolean.getBoolean("lab.entsleep.debug");
    /** -Dlab.entsleep.noSleepFor=A,B: these classes are observed but never put to sleep (diagnosis) */
    private static final java.util.Set<String> NO_SLEEP_FOR = java.util.Set.of(System.getProperty("lab.entsleep.noSleepFor", "").split(","));
    private static int dbg, dbgP;

    enum St { OBSERVE, ASLEEP, NEVER }

    /** Fields a replay may not write raw: position bookkeeping, identity, synched data (it has its own dirty tracking). */
    private static final Set<String> FORBID_V = Set.of("position", "blockPosition", "chunkPosition", "bb", "feetBlockState", "levelCallback",
            "removalReason", "entityData", "level", "xo", "yo", "zo", "xOld", "yOld", "zOld", "passengers", "vehicle", "uuid", "id");

    // ------------------------------------------------------------------ per class

    static final class EClass {
        final SleepAnalysis.Plan plan;
        String refuse;
        final Field[] f;
        final int n;
        SnapCompiler.Snapper snap;
        long analysisMs;
        int tickCount = -1;
        int[] clocks = new int[0];
        int[] nulls = new int[0], zeros = new int[0];
        int[] entityRefs = new int[0];
        boolean[] special;    // tickCount and clocks: compared by rule, not with the base value
        boolean[] prim;
        int[] refSlots;       // references the replay check compares (non-final), in order
        int[] refOrd;         // slot -> position in refSlots (-1: not compared)
        boolean[] forbidV;
        final int period;

        EClass(Class<?> c) {
            long t0 = System.nanoTime();
            plan = SleepAnalysis.analyzeEntity(c);
            analysisMs = (System.nanoTime() - t0) / 1_000_000;
            List<Field> fl = new ArrayList<>();
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field x : k.getDeclaredFields()) {
                    if (Modifier.isStatic(x.getModifiers())) continue;
                    try {
                        x.setAccessible(true);
                        fl.add(x);
                    } catch (RuntimeException e) {
                        refuse = "field not accessible: " + x.getName();
                    }
                }
            }
            f = fl.toArray(new Field[0]);
            n = f.length;
            period = (int) Math.min(Integer.MAX_VALUE, plan.period());
            if (!plan.eligible()) refuse = plan.reason();
            else if (period > MAX_PERIOD) refuse = "period " + period + " > " + MAX_PERIOD;
            else if (!EntityFp.available()) refuse = "fingerprints unavailable";
            if (refuse != null) return;
            special = new boolean[n];
            forbidV = new boolean[n];
            prim = new boolean[n];
            for (int i = 0; i < n; i++) prim[i] = f[i].getType().isPrimitive();
            List<Integer> cl = new ArrayList<>(), nu = new ArrayList<>(), er = new ArrayList<>(), ze = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Field x = f[i];
                if (x.getName().equals("tickCount") && x.getDeclaringClass() == Entity.class) {
                    tickCount = i;
                    special[i] = true;
                    continue;
                }
                if (plan.clocks().contains(x)) {
                    if (!(x.getType() == int.class || x.getType() == long.class || x.getType() == short.class || x.getType() == byte.class)) {
                        refuse = "clock of type " + x.getType();
                        return;
                    }
                    cl.add(i);
                    special[i] = true;
                }
                if (plan.nulls().contains(x)) nu.add(i);
                if (plan.zeros().contains(x)) ze.add(i);
                if (Entity.class.isAssignableFrom(x.getType())) er.add(i);
                forbidV[i] = FORBID_V.contains(x.getName()) && x.getDeclaringClass() == Entity.class;
            }
            if (tickCount < 0) refuse = "no tickCount";
            // one compiled class: snap() by value for observation, same() by value/identity for the replay check
            snap = SnapCompiler.compile(fl, true, special);
            refSlots = SnapCompiler.refSlots(fl, special);
            refOrd = new int[n];
            java.util.Arrays.fill(refOrd, -1);
            for (int k = 0; k < refSlots.length; k++) refOrd[refSlots[k]] = k;
            if (SnapCompiler.failure != null) System.out.println("[probe/entsleep] snapshot fallback (reflection): " + SnapCompiler.failure);
            clocks = cl.stream().mapToInt(Integer::intValue).toArray();
            nulls = nu.stream().mapToInt(Integer::intValue).toArray();
            zeros = ze.stream().mapToInt(Integer::intValue).toArray();
            entityRefs = er.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static final Map<Class<?>, EClass> CLASSES = new java.util.concurrent.ConcurrentHashMap<>();   // filled by AsyncAnalysis

    // ------------------------------------------------------------------ per entity

    static final class ES {
        final Entity e;
        final EClass c;
        final long id;
        St st = St.OBSERVE;
        // observation window
        int k = -1;
        int lastTick, startTick;
        long[] base, prevA, rates;
        Object[] baseRefs;
        LongArrayList diffs;          // pairs: ((k * 2 + after) << 16 | field), value
        ArrayList<Object> diffRefs;   // the object behind each after-diff of a reference field (null otherwise)
        long[] readHashAt;
        AABB blockBox, entityBox;     // union of the window's reads
        boolean entityExposed;        // the tick got entity objects (getEntities): neighbour rule
        int failStreak;
        int observeFrom;              // tickCount before which it is not observed (back-off)
        // asleep
        int[] v;
        long[][] aV, bV;
        Object[][] aRef;
        long[] hashB;                 // per phase: hash of the primitives expected before the tick
        Object[] expR;                // expected references (identity), compact (EClass.refSlots)
        long[] expClock;
        long wakeAt;
        boolean woken;
        String wakeReason;
        int subEpoch;

        ES(Entity e, EClass c) {
            this.e = e;
            this.c = c;
            this.id = ++nextId;
        }
    }

    private static long nextId;
    static ES obs;
    static Thread obsThread;
    private static long w0, seed0, u0, readHash;
    static long unobservedE;
    private static long[] SB = new long[64], SA = new long[64];

    // stats
    static long observedTicks, entered, replayed, verifyFails, deadlineWakes;
    static final Map<String, Long> WAKES = new TreeMap<>();
    static final Map<String, Long> REFUSED = new TreeMap<>();

    private EntitySleep() {}

    private static ES es(Entity e) {
        return (ES) ((EntSleeperHolder) e).probe$es();
    }

    // ------------------------------------------------------------------ the tick

    /** Replaces the entity.tick() call in ServerLevel.tickNonPassenger. */
    public static void tick(Entity e) {
        if (DIAG != null && e.getClass().getSimpleName().equals(DIAG)) {
            diag(e);
            return;
        }
        if (!ON) {
            e.tick();
            return;
        }
        Level level = e.level();
        long now = level.getGameTime();
        if (now != lastPoll) poll(now);
        ES s = es(e);
        if (s == null) {
            EClass c = AsyncAnalysis.get(CLASSES, e.getClass(), k -> {
                EClass x = new EClass(k);
                System.out.println("[probe/entsleep] " + k.getSimpleName() + " (" + x.analysisMs + " ms): " + (x.refuse == null ? "ELIGIBLE fields " + x.n + " (final "
                        + java.util.Arrays.stream(x.f).filter(q -> Modifier.isFinal(q.getModifiers())).count() + ", primitive "
                        + java.util.Arrays.stream(x.f).filter(q -> q.getType().isPrimitive()).count() + ") period " + x.period
                        + " clocks=" + java.util.Arrays.stream(x.clocks).mapToObj(i -> x.f[i].getName()).toList()
                        + " conds=" + x.plan.conds().stream().map(SleepAnalysis.Cond::text).toList() : "no: " + x.refuse));
                return x;
            });
            if (c == null) {   // its class is being analysed in the background: vanilla's tick meanwhile
                e.tick();
                return;
            }
            s = new ES(e, c);
            if (c.refuse != null || e.getBlockX() < MIN_X || dev.probe.api.Compat.entitySleepExcluded(e)) s.st = St.NEVER;
            ((EntSleeperHolder) e).probe$setEs(s);
        }
        if (s.st == St.ASLEEP) {
            if (!s.woken && now < s.wakeAt && replay(s)) {
                replayed++;
                return;
            }
            wake(s, s.woken ? s.wakeReason : now >= s.wakeAt ? "deadline" : "state differs");
        }
        if (s.st == St.NEVER || e.tickCount < s.observeFrom || level.isClientSide) {
            e.tick();
            return;
        }
        observe(s, level);
    }

    private static void observe(ES s, Level level) {
        Entity e = s.e;
        EClass c = s.c;
        int n = c.n;
        if (SB.length < n) {
            SB = new long[n * 2];
            SA = new long[n * 2];
        }
        long[] b = SB, a = SA;
        c.snap.snap(e, b);
        boolean cont = s.k >= 0 && e.tickCount == s.lastTick + 1;
        obs = s;
        obsThread = Thread.currentThread();
        w0 = BeTickStats.writes;
        seed0 = SleepEngine.seed(level);
        u0 = unobservedE;
        readHash = 17;
        int L = c.period;
        if (!cont) {
            s.blockBox = null;
            s.entityBox = null;
            s.entityExposed = false;
            if (s.base == null || s.base.length != n) {
                s.base = new long[n];
                s.prevA = new long[n];
                s.baseRefs = new Object[n];
                s.rates = new long[c.clocks.length];
                s.diffs = new LongArrayList();
                s.diffRefs = new ArrayList<>();
                s.readHashAt = new long[L + 1];
            }
            c.snap.refs(e, s.baseRefs);   // the objects of the state before tick 0
        }
        try {
            e.tick();
        } finally {
            obs = null;
        }
        observedTicks++;
        c.snap.snap(e, a);
        boolean quiet = BeTickStats.writes == w0 && SleepEngine.seed(level) == seed0 && unobservedE == u0 && !e.isRemoved();
        if (!quiet) {
            s.k = -1;
            fail(s, "busy");
            return;
        }
        if (!cont) {
            System.arraycopy(b, 0, s.base, 0, n);
            for (int j = 0; j < c.clocks.length; j++) s.rates[j] = a[c.clocks[j]] - b[c.clocks[j]];
            s.diffs.clear();
            s.diffRefs.clear();
            s.k = 0;
            s.startTick = e.tickCount;
        } else {
            s.k++;
            // clocks: no change outside the tick, the same change inside it
            for (int j = 0; j < c.clocks.length; j++) {
                int i = c.clocks[j];
                if (b[i] != s.prevA[i] || a[i] - b[i] != s.rates[j]) {
                    s.k = -1;
                    fail(s, "clock rate changed");
                    return;
                }
            }
        }
        s.lastTick = e.tickCount;
        s.readHashAt[s.k] = readHash;
        if (s.k == L) {
            // state before tick L must be the state before tick 0
            for (int i = 0; i < n; i++) {
                if (!c.special[i] && b[i] != s.base[i]) {
                    s.k = -1;
                    if (DEBUG && dbgP++ < 6) System.out.println("[probe/entsleep] not periodic: " + c.f[i].getName() + " #" + e.getId() + " t=" + e.tickCount + " now " + show(c.f[i], e));
                    fail(s, "not periodic");
                    return;
                }
            }
            if (s.readHashAt[L] != s.readHashAt[0]) {
                s.k = -1;
                fail(s, "reads not periodic");
                return;
            }
            trySleep(s, level);
            return;
        }
        // record differences from the base (before and after this tick)
        int kk = s.k;
        for (int i = 0; i < n; i++) {
            if (c.special[i]) continue;
            if (b[i] != s.base[i] && kk > 0) {
                s.diffs.add(((long) (kk * 2) << 16) | i);
                s.diffs.add(b[i]);
                s.diffRefs.add(null);
            }
            if (a[i] != s.base[i]) {
                s.diffs.add(((long) (kk * 2 + 1) << 16) | i);
                s.diffs.add(a[i]);
                s.diffRefs.add(c.f[i].getType().isPrimitive() ? null : get(c.f[i], e));
            }
        }
        System.arraycopy(a, 0, s.prevA, 0, n);
        if (s.diffs.size() > MAX_DIFFS) {
            s.k = -1;
            fail(s, "too many changes");
        }
    }

    private static void fail(ES s, String why) {
        if (++s.failStreak >= 4) {
            // back off: busy entities are observed less and less often
            int wait = Math.min(400, 20 << Math.min(4, s.failStreak - 4));
            s.observeFrom = s.e.tickCount + wait;
        }
        refuse(why);
    }

    private static void refuse(String why) {
        REFUSED.merge(why, 1L, Long::sum);
    }

    private static void trySleep(ES s, Level level) {
        Entity e = s.e;
        EClass c = s.c;
        int L = c.period;
        s.k = -1;
        if (NO_SLEEP_FOR.contains(e.getClass().getSimpleName())) { fail(s, "debug: never sleeps"); return; }
        for (int i : c.nulls) if (get(c.f[i], e) != null) { fail(s, "needs null " + c.f[i].getName()); return; }
        for (int i : c.zeros) if (((Number) get(c.f[i], e)).longValue() != 0) { fail(s, "needs zero " + c.f[i].getName()); return; }
        for (int i : c.entityRefs) if (get(c.f[i], e) != null) { fail(s, "holds entity " + c.f[i].getName()); return; }
        if (e.isPassenger() || e.isVehicle()) { fail(s, "riding"); return; }
        // the fields that vary within the period
        boolean[] inV = new boolean[c.n];
        int nv = 0;
        for (int d = 0; d < s.diffs.size(); d += 2) {
            int i = (int) (s.diffs.getLong(d) & 0xFFFF);
            if (!inV[i]) {
                inV[i] = true;
                nv++;
            }
        }
        int[] v = new int[nv];
        for (int i = 0, j = 0; i < c.n; i++) if (inV[i]) v[j++] = i;
        for (int i : v) {
            if (c.forbidV[i]) { fail(s, "varies " + c.f[i].getName()); return; }
            Class<?> t = c.f[i].getType();
            if (!t.isPrimitive() && !valueType(t)) { fail(s, "varying reference " + c.f[i].getName()); return; }
        }
        long[][] aV = new long[L][nv], bV = new long[L][nv];
        Object[][] aRef = new Object[L][];
        for (int p = 0; p < L; p++) {
            for (int j = 0; j < nv; j++) {
                aV[p][j] = s.base[v[j]];
                bV[p][j] = s.base[v[j]];
            }
        }
        for (int j = 0; j < nv; j++) {
            if (c.f[v[j]].getType().isPrimitive()) continue;
            for (int p = 0; p < L; p++) {
                if (aRef[p] == null) aRef[p] = new Object[nv];
                aRef[p][j] = s.baseRefs[v[j]];
            }
        }
        for (int d = 0, r = 0; d < s.diffs.size(); d += 2, r++) {
            long key = s.diffs.getLong(d);
            int i = (int) (key & 0xFFFF), ka = (int) (key >>> 16), kk = ka >> 1;
            int j = java.util.Arrays.binarySearch(v, i);
            if ((ka & 1) == 1) {
                aV[kk][j] = s.diffs.getLong(d + 1);
                if (aRef[kk] != null) aRef[kk][j] = s.diffRefs.get(r);
            } else {
                bV[kk][j] = s.diffs.getLong(d + 1);
            }
        }
        // a varying reference must be a value object (immutable, shared safely)
        for (int p = 0; p < L; p++) {
            if (aRef[p] == null) continue;
            for (Object o : aRef[p]) if (o != null && !valueType(o.getClass())) { fail(s, "varying mutable " + o.getClass().getSimpleName()); return; }
        }
        if (s.entityExposed && !neighboursAsleep(s)) { fail(s, "neighbours awake"); return; }
        long d = deadline(s);
        if (d <= L + 1) { fail(s, "deadline too close"); return; }
        // a varying reference must not be replaced outside the tick (its expected identity is what the replay wrote)
        for (int j = 0; j < nv; j++) {
            if (c.f[v[j]].getType().isPrimitive()) continue;
            for (int p = 0; p < L; p++) if (bV[p][j] != aV[Math.floorMod(p - 1, L)][j]) { fail(s, "reference changed outside the tick: " + c.f[v[j]].getName()); return; }
        }
        long[] cur = new long[c.n];
        c.snap.snap(e, cur);
        Object[] refs = new Object[c.n];
        c.snap.refs(e, refs);
        s.expR = new Object[c.refSlots.length];
        for (int k = 0; k < c.refSlots.length; k++) s.expR[k] = refs[c.refSlots[k]];
        s.hashB = new long[L];
        for (int p = 0; p < L; p++) {
            for (int j = 0; j < nv; j++) if (c.prim[v[j]]) cur[v[j]] = bV[p][j];
            s.hashB[p] = SnapCompiler.hashPrims(cur, c.prim, c.special);
        }
        s.expClock = new long[c.clocks.length];
        for (int j = 0; j < c.clocks.length; j++) s.expClock[j] = c.snap.bits(e, c.clocks[j]);
        s.v = v;
        s.aV = aV;
        s.bV = bV;
        s.aRef = aRef;
        s.wakeAt = d == Long.MAX_VALUE ? Long.MAX_VALUE : level.getGameTime() + d;
        s.woken = false;
        s.st = St.ASLEEP;
        s.failStreak = 0;
        s.diffs.clear();
        s.diffRefs.clear();
        entered++;
        subscribe(s);
        if (s.wakeAt != Long.MAX_VALUE) {
            DEADLINES.add(s.wakeAt, s.id);
            SCHEDULED.put(s.id, s);
        }
    }

    private static boolean valueType(Class<?> t) {
        return t == Vec3.class || t == AABB.class || t == BlockPos.class || t == Boolean.class || t == Integer.class || t == Long.class
                || t == Float.class || t == Double.class || t == Short.class || t == Byte.class || t == String.class || t.isEnum()
                || t == java.util.Optional.class || t == Object.class && false;
    }

    /** Every entity in the boxes whose objects the tick received must be asleep (its state can then only change with an event). */
    private static boolean neighboursAsleep(ES s) {
        if (s.entityBox == null) return true;
        for (Entity x : s.e.level().getEntities(s.e, s.entityBox)) {
            ES xs = es(x);
            if (xs == null || xs.st != St.ASLEEP) return false;
        }
        return true;
    }

    /** Ticks until a clock condition can change its outcome (Long.MAX_VALUE: never while the rates hold). */
    private static long deadline(ES s) {
        EClass c = s.c;
        Entity e = s.e;
        long best = Long.MAX_VALUE;
        for (SleepAnalysis.Cond cond : c.plan.conds()) {
            if (cond.clockMod()) continue;   // inside the period
            if (cond.clock() == null) return 0;   // game-time modulo: not aligned with the entity's phase
            long cv, r;
            int ci = index(c, cond.clock());
            if (ci < 0) return 0;
            cv = longValue(c.f[ci], e);
            r = ci == c.tickCount ? 1 : rateOf(s, ci);
            if (cond.peer()) {
                if (!s.entityExposed) continue;   // no neighbour object reached the tick in the observed period
                long dd = peerDeadline(s, ci, cv, r);
                best = Math.min(best, dd);
                continue;
            }
            if (r == 0) continue;
            long t = cond.thresholdConst();
            if (cond.thresholdField() != null) {
                int ti = index(c, cond.thresholdField());
                if (ti < 0) return 0;
                t += longValue(c.f[ti], e);
            }
            long dist = r > 0 ? t - cv : cv - t;
            if (dist < -1) continue;   // passed and moving away
            best = Math.min(best, Math.max(0, dist / Math.abs(r) - 1));
        }
        return best;
    }

    /** this.clock ? other.clock for other entities of the same class in the read boxes: when their order can flip. */
    private static long peerDeadline(ES s, int ci, long cv, long r) {
        if (s.entityBox == null) return Long.MAX_VALUE;
        long best = Long.MAX_VALUE;
        for (Entity x : s.e.level().getEntities(s.e, s.entityBox)) {
            if (x.getClass() != s.e.getClass()) continue;
            ES xs = es(x);
            if (xs == null || xs.st != St.ASLEEP) return 0;
            long xv = longValue(s.c.f[ci], x), xr = ci == s.c.tickCount ? 1 : rateOf(xs, ci);
            long dr = r - xr, diff = cv - xv;
            if (dr == 0) continue;
            long t = -diff / dr;   // ticks until equal
            if (t < -1) continue;
            best = Math.min(best, Math.max(0, t - 1));
        }
        return best;
    }

    private static long rateOf(ES s, int fieldIndex) {
        int[] cl = s.c.clocks;
        for (int j = 0; j < cl.length; j++) if (cl[j] == fieldIndex) return s.rates[j];
        return 0;
    }

    private static int index(EClass c, Field f) {
        for (int i = 0; i < c.n; i++) if (c.f[i].equals(f)) return i;
        return -1;
    }

    /** One skipped tick: check the state is the expected one for this phase, write the tick's results, advance clocks. */
    private static boolean replay(ES s) {
        Entity e = s.e;
        EClass c = s.c;
        int L = c.period;
        long el = (long) e.tickCount - s.startTick;
        int p = (int) Math.floorMod(el, (long) L);
        int[] v = s.v;
        boolean[] prim = c.prim;
        SnapCompiler.Snapper sn = c.snap;
        int[] cl = c.clocks;
        if (VERIFY) {
            // every field: primitives by a hash of their values, references by identity (the objects left by the last
            // replay); synched data changes arrive through its set() hook. Clocks separately.
            if (sn.hashPrims(e) != s.hashB[p] || !sn.sameRefs(e, s.expR)) return verifyFail(s, firstDiff(s, p));
            for (int j = 0; j < cl.length; j++) if (sn.bits(e, cl[j]) != s.expClock[j]) return verifyFail(s, "clock " + c.f[cl[j]].getName());
        }
        long[] ap = s.aV[p];
        Object[] ar = s.aRef[p];
        for (int j = 0; j < v.length; j++) {
            int i = v[j];
            if (prim[i]) {
                sn.put(e, i, ap[j], null);
            } else {
                sn.put(e, i, 0, ar[j]);
                int k = c.refOrd[i];
                if (k >= 0) s.expR[k] = ar[j];
            }
        }
        long[] rates = s.rates;
        for (int j = 0; j < cl.length; j++) {
            long r = rates[j];
            if (r == 0) continue;
            long x = narrow(c.f[cl[j]].getType(), s.expClock[j] + r);
            s.expClock[j] = x;
            sn.put(e, cl[j], x, null);
        }
        return true;
    }

    /** Which field broke the replay check (diagnostics, slow). */
    private static String firstDiff(ES s, int p) {
        EClass c = s.c;
        for (int k = 0; k < c.refSlots.length; k++) if (get(c.f[c.refSlots[k]], s.e) != s.expR[k]) return c.f[c.refSlots[k]].getName();
        long[] one = new long[c.n];
        c.snap.snap(s.e, one);
        // primitives: which one differs from the phase's expected values
        long[] exp = one.clone();
        for (int i = 0; i < c.n; i++) if (c.prim[i] && !c.special[i]) exp[i] = s.base[i];
        for (int j = 0; j < s.v.length; j++) if (c.prim[s.v[j]]) exp[s.v[j]] = s.bV[p][j];
        for (int i = 0; i < c.n; i++) if (c.prim[i] && !c.special[i] && one[i] != exp[i]) return c.f[i].getName();
        return "primitive hash";
    }

    private static boolean verifyFail(ES s, String field) {
        verifyFails++;
        if (DEBUG && dbg++ < 12) {
            long el = (long) s.e.tickCount - s.startTick;
            StringBuilder sb = new StringBuilder("[probe/entsleep] verify fail " + field + " #" + s.e.getId() + " el=" + el + " phase=" + Math.floorMod(el, (long) s.c.period) + " now=");
            for (int j = 0; j < s.v.length; j++) sb.append(s.c.f[s.v[j]].getName()).append('=').append(show(s.c.f[s.v[j]], s.e)).append(' ');
            sb.append(" table:");
            for (int p = 0; p < Math.min(8, s.c.period); p++) {
                sb.append(" [").append(p).append(']');
                for (int j = 0; j < s.v.length; j++) sb.append(' ').append(Long.toHexString(s.bV[p][j] & 0xFFFF)).append('>').append(s.aRef[p] != null && s.aRef[p][j] != null ? s.aRef[p][j] : Long.toHexString(s.aV[p][j] & 0xFFFF));
            }
            System.out.println(sb);
        }
        s.wakeReason = "changed: " + field;
        s.woken = true;
        return false;
    }

    private static void wake(ES s, String reason) {
        s.st = St.OBSERVE;
        s.k = -1;
        s.woken = false;
        s.v = null;
        s.aV = s.bV = null;
        s.aRef = null;
        s.subEpoch++;
        SCHEDULED.remove(s.id);
        WAKES.merge(reason.startsWith("changed: ") ? reason : reason, 1L, Long::sum);
        onEntity(s.e, "neighbour woke");   // readers of this entity may depend on it being asleep
    }

    private static void request(ES s, String reason) {
        if (s.st == St.ASLEEP && !s.woken) {
            s.woken = true;
            s.wakeReason = reason;
        }
    }

    // ------------------------------------------------------------------ deadlines

    private static final LabScheduler DEADLINES = new LabScheduler();
    private static final Long2ObjectOpenHashMap<ES> SCHEDULED = new Long2ObjectOpenHashMap<>();
    private static long lastPoll = Long.MIN_VALUE;

    private static void poll(long now) {
        lastPoll = now;
        if (DEADLINES.size() == 0) return;
        for (long id; (id = DEADLINES.takeDue(now)) != LabScheduler.NONE; ) {
            ES s = SCHEDULED.get(id);
            if (s == null) continue;
            if (s.st != St.ASLEEP || s.e.isRemoved()) {
                SCHEDULED.remove(id);
            } else if (s.wakeAt <= now) {
                SCHEDULED.remove(id);
                deadlineWakes++;
                request(s, "deadline");
            }
        }
        DEADLINES.endTick(now);
    }

    // ------------------------------------------------------------------ reads (observation) and subscriptions

    static void readPos(char kind, long p) {
        ES s = obs;
        int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
        s.blockBox = union(s.blockBox, x, y, z, x + 1, y + 1, z + 1);
        readHash = readHash * 1_000_003L + kind * 31L + p;
    }

    static void readBox(char kind, AABB box) {
        ES s = obs;
        s.entityBox = s.entityBox == null ? box : s.entityBox.minmax(box);
        if (kind != 'C') s.entityExposed = true;
        readHash = readHash * 1_000_003L + kind * 31L + Double.doubleToLongBits(box.minX) * 7 + Double.doubleToLongBits(box.minY) * 13
                + Double.doubleToLongBits(box.minZ) * 17 + Double.doubleToLongBits(box.maxX + box.maxY + box.maxZ);
    }

    /** Block collision / support queries: every block within the box (and one around it) is read. */
    static void readRegion(AABB box) {
        ES s = obs;
        s.blockBox = union(s.blockBox, Math.floor(box.minX) - 1, Math.floor(box.minY) - 1, Math.floor(box.minZ) - 1,
                Math.floor(box.maxX) + 2, Math.floor(box.maxY) + 2, Math.floor(box.maxZ) + 2);
        readHash = readHash * 1_000_003L + 'R' * 31L + Double.doubleToLongBits(box.minX) * 7 + Double.doubleToLongBits(box.minY) * 13
                + Double.doubleToLongBits(box.minZ) * 17 + Double.doubleToLongBits(box.maxX + box.maxY + box.maxZ);
    }

    static void unobservedRead() {
        unobservedE++;
    }

    private static AABB union(AABB b, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (b == null) return new AABB(x0, y0, z0, x1, y1, z1);
        if (x0 >= b.minX && y0 >= b.minY && z0 >= b.minZ && x1 <= b.maxX && y1 <= b.maxY && z1 <= b.maxZ) return b;
        return new AABB(Math.min(x0, b.minX), Math.min(y0, b.minY), Math.min(z0, b.minZ), Math.max(x1, b.maxX), Math.max(y1, b.maxY), Math.max(z1, b.maxZ));
    }

    /** Subscribers per section, with lazy removal (an entry is live while its sleeper is asleep with the same epoch). */
    static final class SubList {
        ES[] es = new ES[4];
        int[] ep = new int[4];
        int n;

        void add(ES s) {
            if (n == es.length) {
                compact();
                if (n == es.length) {
                    es = java.util.Arrays.copyOf(es, n * 2);
                    ep = java.util.Arrays.copyOf(ep, n * 2);
                }
            }
            es[n] = s;
            ep[n++] = s.subEpoch;
        }

        void compact() {
            int m = 0;
            for (int i = 0; i < n; i++) {
                ES s = es[i];
                if (s.st == St.ASLEEP && ep[i] == s.subEpoch && !s.e.isRemoved()) {
                    es[m] = s;
                    ep[m++] = ep[i];
                }
            }
            for (int i = m; i < n; i++) es[i] = null;
            n = m;
        }
    }

    private static final Long2ObjectOpenHashMap<SubList> BLOCK_SUBS = new Long2ObjectOpenHashMap<>(), ENTITY_SUBS = new Long2ObjectOpenHashMap<>();

    private static void subscribe(ES s) {
        if (s.blockBox != null) addSections(BLOCK_SUBS, s, s.blockBox);
        if (s.entityBox != null) addSections(ENTITY_SUBS, s, s.entityBox);
    }

    private static void addSections(Long2ObjectOpenHashMap<SubList> map, ES s, AABB b) {
        int x0 = SectionPos.posToSectionCoord(b.minX), x1 = SectionPos.posToSectionCoord(b.maxX);
        int y0 = SectionPos.posToSectionCoord(b.minY), y1 = SectionPos.posToSectionCoord(b.maxY);
        int z0 = SectionPos.posToSectionCoord(b.minZ), z1 = SectionPos.posToSectionCoord(b.maxZ);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            SubList l = map.get(SectionPos.asLong(x, y, z));
            if (l == null) map.put(SectionPos.asLong(x, y, z), l = new SubList());
            l.add(s);
        }
    }

    /** A block (or block entity / container) changed at pos. */
    static void onPos(Level level, BlockPos pos) {
        if (BLOCK_SUBS.isEmpty()) return;
        SubList l = BLOCK_SUBS.get(SectionPos.asLong(pos));
        if (l == null) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < l.n; i++) {
            ES s = l.es[i];
            if (s.st != St.ASLEEP || l.ep[i] != s.subEpoch || s.e.level() != level) continue;
            AABB b = s.blockBox;
            if (x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ) request(s, "block");
        }
    }

    /** An entity moved, appeared, changed or was removed; or a neighbour woke. */
    static void onEntity(Entity x, String reason) {
        ES self = es(x);
        if (self != null && self.st == St.ASLEEP && !reason.equals("neighbour woke")) request(self, "self: " + reason);
        if (ENTITY_SUBS.isEmpty()) return;
        Level level = x.level();
        if (level == null || level.isClientSide) return;
        AABB bb = x.getBoundingBox();
        int x0 = SectionPos.posToSectionCoord(bb.minX), x1 = SectionPos.posToSectionCoord(bb.maxX);
        int y0 = SectionPos.posToSectionCoord(bb.minY), y1 = SectionPos.posToSectionCoord(bb.maxY);
        int z0 = SectionPos.posToSectionCoord(bb.minZ), z1 = SectionPos.posToSectionCoord(bb.maxZ);
        for (int sx = x0; sx <= x1; sx++) for (int sy = y0; sy <= y1; sy++) for (int sz = z0; sz <= z1; sz++) {
            SubList l = ENTITY_SUBS.get(SectionPos.asLong(sx, sy, sz));
            if (l == null) continue;
            for (int i = 0; i < l.n; i++) {
                ES s = l.es[i];
                if (s == self || s.st != St.ASLEEP || l.ep[i] != s.subEpoch || s.e.level() != level) continue;
                if (s.entityBox.intersects(bb)) request(s, reason);
            }
        }
    }

    public static void onDataSet(Entity e, EntityDataAccessor<?> key, Object value) {
        ES self = es(e);
        if ((self == null || self.st != St.ASLEEP) && ENTITY_SUBS.isEmpty()) return;
        if (java.util.Objects.equals(e.getEntityData().get(key), value)) return;   // vanilla's no-op
        onEntity(e, "data");
    }

    // ------------------------------------------------------------------ field access (slow path, a few per replay)

    private static Object get(Field f, Object o) {
        try {
            return f.get(o);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void set(Field f, Object o, Object v) {
        try {
            f.set(o, v);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long longValue(Field f, Object o) {
        try {
            Class<?> t = f.getType();
            if (t == int.class) return f.getInt(o);
            if (t == long.class) return f.getLong(o);
            if (t == short.class) return f.getShort(o);
            if (t == byte.class) return f.getByte(o);
            return 0;
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long narrow(Class<?> t, long v) {
        if (t == int.class) return (int) v;
        if (t == short.class) return (short) v;
        if (t == byte.class) return (byte) v;
        return v;
    }

    /** Write a primitive field from the snapshot encoding (raw bits for float/double, 0/1 for boolean). */
    private static void setBits(Field f, Object o, long bits) {
        try {
            Class<?> t = f.getType();
            if (t == int.class) f.setInt(o, (int) bits);
            else if (t == long.class) f.setLong(o, bits);
            else if (t == boolean.class) f.setBoolean(o, bits != 0);
            else if (t == short.class) f.setShort(o, (short) bits);
            else if (t == byte.class) f.setByte(o, (byte) bits);
            else if (t == char.class) f.setChar(o, (char) bits);
            else if (t == float.class) f.setFloat(o, Float.intBitsToFloat((int) bits));
            else if (t == double.class) f.setDouble(o, Double.longBitsToDouble(bits));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ report

    public static String report() {
        Map<String, int[]> byType = new TreeMap<>();
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "entity sleep: %s; observed ticks %d, sleeps entered %d, replayed ticks %d, verify fails %d, deadline wakes %d, deadlines pending %d%n",
                ON ? "ON" : "OFF", observedTicks, entered, replayed, verifyFails, deadlineWakes, DEADLINES.size()));
        sb.append("  wakes: ").append(WAKES).append(System.lineSeparator());
        sb.append("  refused: ").append(REFUSED).append(System.lineSeparator());
        return sb.toString();
    }

    /** Asleep / tracked counts per entity type in a level. */
    public static String census(net.minecraft.server.level.ServerLevel level) {
        Map<String, int[]> byType = new TreeMap<>();
        for (Entity e : level.getAllEntities()) {
            ES s = es(e);
            int[] v = byType.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), k -> new int[3]);
            v[0]++;
            if (s != null && s.st == St.ASLEEP) v[1]++;
            if (s != null && s.st == St.NEVER) v[2]++;
        }
        StringBuilder sb = new StringBuilder();
        byType.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "  %-34s entities %6d  asleep %6d  never %6d%n", k, v[0], v[1], v[2])));
        return sb.toString();
    }

    /**
     * Exactness checks ({@code probe hash} with -Dprobe.hashEntitiesDeep=true): every primitive field and every value
     * object (vectors, boxes, positions, synched data, random seeds, boxed values, enums) of the entity, by value.
     * Identity-only references, ids and UUIDs are left out (they differ between runs without meaning anything).
     */
    /** Like EntityFp.fp but equal across runs: item stacks by registry id, count and components; synched values by content. */
    private static long stableFp(Object r) {
        if (r instanceof net.minecraft.world.item.ItemStack st)
            return st.isEmpty() ? 0 : BuiltInRegistries.ITEM.getKey(st.getItem()).hashCode() * 1_000_003L + st.getCount() * 7919L + st.getComponentsPatch().toString().hashCode();
        if (r instanceof SynchedEntityData d) {
            long h = d.isDirty() ? 1 : 0;
            for (SynchedEntityData.DataValue<?> dv : d.getNonDefaultValues() == null ? List.<SynchedEntityData.DataValue<?>>of() : d.getNonDefaultValues())
                h = h * 31 + dv.id() * 7919L + stableFp(dv.value());
            return h;
        }
        if (r instanceof Vec3 || r instanceof AABB || r instanceof BlockPos) return EntityFp.fp(r);
        if (r instanceof Enum<?> en) return en.getDeclaringClass().getName().hashCode() * 31L + en.ordinal();   // Enum.hashCode is per run
        return r == null ? 0 : r.toString().hashCode();
    }

    public static String deepDump(Entity e) {
        StringBuilder sb = new StringBuilder();
        for (Field f : DFIELDS.computeIfAbsent(e.getClass(), EntitySleep::instanceFields)) sb.append(f.getName()).append('=').append(show(f, e)).append("; ");
        return sb.toString();
    }

    public static long deepHash(Entity e) {
        Field[] fs = DFIELDS.computeIfAbsent(e.getClass(), EntitySleep::instanceFields);
        long h = 17;
        for (Field f : fs) {
            String n = f.getName();
            if (n.equals("id") || n.equals("uuid") || n.equals("stringUUID") || n.startsWith("probe$") || n.equals("bobOffs")) continue;   // per run
            Class<?> t = f.getType();
            // (random seeds are per run: RandomSource.create() is seeded from the clock)
            if (!t.isPrimitive() && !(valueType(t) || SynchedEntityData.class.isAssignableFrom(t))) continue;
            try {
                long v = t == int.class ? f.getInt(e) : t == long.class ? f.getLong(e) : t == boolean.class ? (f.getBoolean(e) ? 1 : 0)
                        : t == short.class ? f.getShort(e) : t == byte.class ? f.getByte(e) : t == float.class ? Float.floatToRawIntBits(f.getFloat(e))
                        : t == double.class ? Double.doubleToRawLongBits(f.getDouble(e)) : t == char.class ? f.getChar(e) : stableFp(f.get(e));
                h = h * 1_000_003L + n.hashCode() * 31L + v;
            } catch (IllegalAccessException ex) {
                h = h * 31 + 1;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ diagnostic

    private static final Map<Class<?>, Field[]> DFIELDS = new HashMap<>();
    private static final Map<Integer, long[]> DPREV = new HashMap<>();
    private static final List<Integer> PICKED = new ArrayList<>();
    private static final int DIAG_FROM = Integer.getInteger("lab.entsleep.diagFrom", 300), DIAG_TICKS = Integer.getInteger("lab.entsleep.diagTicks", 12);

    private static void diag(Entity e) {
        int id = e.getId();
        if (!PICKED.contains(id)) {
            if (PICKED.size() >= 3 || e.tickCount < DIAG_FROM) {
                e.tick();
                return;
            }
            PICKED.add(id);
        }
        Field[] fs = DFIELDS.computeIfAbsent(e.getClass(), EntitySleep::instanceFields);
        long[] before = dsnap(e, fs);
        e.tick();
        long[] after = dsnap(e, fs);
        long[] prev = DPREV.put(id, after);
        StringBuilder sb = new StringBuilder("[probe/entsleep] ").append(e.getClass().getSimpleName()).append(" #").append(id).append(" t=").append(e.tickCount).append(':');
        for (int i = 0; i < fs.length; i++) if (before[i] != after[i]) sb.append(' ').append(fs[i].getName()).append('=').append(show(fs[i], e));
        if (prev != null) {
            sb.append("  | outside tick:");
            for (int i = 0; i < fs.length; i++) if (prev[i] != before[i]) sb.append(' ').append(fs[i].getName());
        }
        System.out.println(sb);
        if (e.tickCount >= DIAG_FROM + DIAG_TICKS) PICKED.remove((Integer) id);
    }

    static Field[] instanceFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    out.add(f);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return out.toArray(new Field[0]);
    }

    private static long[] dsnap(Entity e, Field[] fs) {
        long[] v = new long[fs.length];
        for (int i = 0; i < fs.length; i++) {
            try {
                Field f = fs[i];
                Class<?> t = f.getType();
                v[i] = t == int.class ? f.getInt(e) : t == long.class ? f.getLong(e) : t == boolean.class ? (f.getBoolean(e) ? 1 : 0)
                        : t == short.class ? f.getShort(e) : t == byte.class ? f.getByte(e) : t == float.class ? Float.floatToRawIntBits(f.getFloat(e))
                        : t == double.class ? Double.doubleToRawLongBits(f.getDouble(e)) : t == char.class ? f.getChar(e) : EntityFp.fp(f.get(e));
            } catch (IllegalAccessException ex) {
                v[i] = 0;
            }
        }
        return v;
    }

    private static String show(Field f, Object o) {
        try {
            Object v = f.get(o);
            String s = v instanceof SynchedEntityData d ? "synched#" + Long.toHexString(EntityFp.fp(d)) : String.valueOf(v);
            return s.length() > 60 ? s.substring(0, 60) : s;
        } catch (IllegalAccessException e) {
            return "?";
        }
    }
}
