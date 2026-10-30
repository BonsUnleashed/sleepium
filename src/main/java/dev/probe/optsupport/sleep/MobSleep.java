package dev.probe.optsupport.sleep;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;

/**
 * Mob sleep at run time (notes/47 steps B and C; -Dlab.opt.EntitySleep with -Dlab.sleep.components and
 * -Dlab.sleep.mobRuntime). Two pieces EntitySleep uses for classes whose plan has a {@link SleepAnalysis.MobPlan}:
 *
 * <p><b>The composite state.</b> The entity's own fields are part 0; every context the analysis followed (the
 * controls, the navigation, the goal selectors, each wrapped goal and goal) is a further part, resolved from the
 * entity by its path. Each part has its own compiled Snapper; {@link Composite} lays them end to end as one Snapper,
 * so EntitySleep's observation, periodicity check, replay writes and replay check work on all of them at once.
 *
 * <p><b>The draws.</b> While an entity is observed, every draw of its own random (LegacyRandomSource, through
 * MobSleepRandomMixin) is recorded with its call site, kind and bound. Each must be at a draw condition of the plan
 * (a draw compared with a constant, a clock or a final field). The replay makes the same draws on the real random -
 * the seed advances exactly as the ticks would have advanced it - and the deadline is the first tick at which a
 * simulated copy of the seed would take another branch at one of them.
 */
final class MobSleep {
    static final boolean ON = Boolean.getBoolean("lab.sleep.components") && Boolean.getBoolean("lab.sleep.mobRuntime");

    private MobSleep() {}

    // ------------------------------------------------------------------ paths

    private static final Map<Class<?>, Map<String, Field>> FIELDS = new HashMap<>();

    static Field field(Class<?> c, String name) {
        Map<String, Field> m = FIELDS.computeIfAbsent(c, k -> new HashMap<>());
        if (m.containsKey(name)) return m.get(name);
        Field r = null;
        for (Class<?> k = c; k != null && k != Object.class && r == null; k = k.getSuperclass())
            try {
                Field f = k.getDeclaredField(name);
                if (!Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    r = f;
                }
            } catch (NoSuchFieldException | RuntimeException e) {
                // the superclass
            }
        m.put(name, r);
        return r;
    }

    static Object fieldValue(Object o, String name) {
        Field f = o == null ? null : field(o.getClass(), name);
        try {
            return f == null ? null : f.get(o);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** the object at a path from the entity ("" the entity, "goalSelector.availableGoals[3].goal") */
    static Object resolve(Object root, String path) {
        if (path.isEmpty()) return root;
        Object o = root;
        for (String part : path.split("\\.")) {
            if (o == null) return null;
            int br = part.indexOf('[');
            o = fieldValue(o, br < 0 ? part : part.substring(0, br));
            if (br >= 0) {
                if (!(o instanceof java.util.Collection<?> c)) return null;
                int k = Integer.parseInt(part.substring(br + 1, part.length() - 1));
                int i = 0;
                Object el = null;
                for (Object x : c) {
                    if (i++ == k) {
                        el = x;
                        break;
                    }
                }
                o = el;
            }
        }
        return o;
    }

    // ------------------------------------------------------------------ the composite state

    /** One object of the state: its fields are slots off .. off + f.length - 1 of the composite. */
    static final class Part {
        final String path;
        final Class<?> cls;
        final Field[] f;
        final int off;
        final boolean[] prim, special;
        final int[] refSlots;   // compact, part-local
        final int refOff;       // where this part's compact references start in the composite's
        final SnapCompiler.Snapper snap;
        final long[] scratch;
        final Object[] rscratch, refScratch;

        Part(String path, Class<?> cls, List<Field> fields, int off, boolean[] special, int refOff) {
            this.path = path;
            this.cls = cls;
            this.f = fields.toArray(new Field[0]);
            this.off = off;
            this.special = special;
            prim = new boolean[f.length];
            for (int i = 0; i < f.length; i++) prim[i] = f[i].getType().isPrimitive();
            snap = SnapCompiler.compile(fields, true, special);
            refSlots = SnapCompiler.refSlots(fields, special);
            this.refOff = refOff;
            scratch = new long[f.length];
            rscratch = new Object[f.length];
            refScratch = new Object[refSlots.length];
        }
    }

    static List<Field> instanceFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                out.add(f);
            }
        return out;
    }

    /**
     * The parts as one Snapper over the entity: each call resolves the parts' objects from the entity's ES (resolved
     * when an observation starts, checked by class). Scratch arrays per part: server thread only.
     */
    static final class Composite implements SnapCompiler.Snapper {
        final Part[] parts;
        final int n;
        final int[] partOf;
        /** one generated class over all the slots (receivers from the ES's context array); null: the per-part path */
        final SnapCompiler.Snapper all;
        final boolean[] prim, special;

        Composite(Part[] parts, List<Field> fields, boolean[] special) {
            this.parts = parts;
            Part last = parts[parts.length - 1];
            n = last.off + last.f.length;
            partOf = new int[n];
            for (int k = 0; k < parts.length; k++) for (int i = 0; i < parts[k].f.length; i++) partOf[parts[k].off + i] = k;
            this.special = special;
            prim = new boolean[n];
            for (int i = 0; i < n; i++) prim[i] = fields.get(i).getType().isPrimitive();
            all = SnapCompiler.compileComposite(fields, special, partOf);
        }

        private static Object[] objs(Object be) {
            return EntitySleep.esOf((Entity) be).ctx;
        }

        @Override
        public void snap(Object be, long[] out) {
            Object[] o = objs(be);
            if (all != null) {
                all.snap(o, out);
                return;
            }
            for (int k = 0; k < parts.length; k++) {
                Part p = parts[k];
                p.snap.snap(o[k], p.scratch);
                System.arraycopy(p.scratch, 0, out, p.off, p.f.length);
            }
        }

        @Override
        public void refs(Object be, Object[] out) {
            Object[] o = objs(be);
            if (all != null) {
                all.refs(o, out);
                return;
            }
            for (int k = 0; k < parts.length; k++) {
                Part p = parts[k];
                // primitive slots untouched: copy only the reference slots
                for (int i = 0; i < p.f.length; i++) p.rscratch[i] = out[p.off + i];
                p.snap.refs(o[k], p.rscratch);
                System.arraycopy(p.rscratch, 0, out, p.off, p.f.length);
            }
        }

        @Override
        public boolean same(Object be, long[] prims, Object[] refs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(Object be, int i, long bits, Object ref) {
            if (all != null) {
                all.put(objs(be), i, bits, ref);
                return;
            }
            int k = partOf[i];
            Part p = parts[k];
            p.snap.put(objs(be)[k], i - p.off, bits, ref);
        }

        @Override
        public long bits(Object be, int i) {
            if (all != null) return all.bits(objs(be), i);
            int k = partOf[i];
            Part p = parts[k];
            return p.snap.bits(objs(be)[k], i - p.off);
        }

        @Override
        public long hashPrims(Object be) {
            Object[] o = objs(be);
            if (all != null) return all.hashPrims(o);
            long h = SnapCompiler.H0;
            for (int k = 0; k < parts.length; k++) h = mix(h, parts[k].snap.hashPrims(o[k]));
            return h;
        }

        /** the Java twin of hashPrims over a composite snapshot */
        long hashOf(long[] v) {
            if (all != null) return SnapCompiler.hashPrims(v, prim, special);
            long h = SnapCompiler.H0;
            for (Part p : parts) {
                System.arraycopy(v, p.off, p.scratch, 0, p.f.length);
                h = mix(h, SnapCompiler.hashPrims(p.scratch, p.prim, p.special));
            }
            return h;
        }

        @Override
        public boolean sameRefs(Object be, Object[] refs) {
            Object[] o = objs(be);
            if (all != null) return all.sameRefs(o, refs);
            for (int k = 0; k < parts.length; k++) {
                Part p = parts[k];
                System.arraycopy(refs, p.refOff, p.refScratch, 0, p.refSlots.length);
                if (!p.snap.sameRefs(o[k], p.refScratch)) return false;
            }
            return true;
        }

        private static long mix(long h, long x) {
            h = (h + x) * SnapCompiler.HM;
            return h ^ (h >>> 31);
        }

        /** the parts' objects for an entity, or null if one is missing or of another class than the plan's */
        Object[] resolveAll(Entity e) {
            Object[] o = new Object[parts.length];
            for (int k = 0; k < parts.length; k++) {
                o[k] = resolve(e, parts[k].path);
                if (o[k] == null || o[k].getClass() != parts[k].cls) return null;
            }
            return o;
        }
    }

    // ------------------------------------------------------------------ assumptions

    private static final Map<String, Method> METHODS = new HashMap<>();

    /** null when every assumption of the plan holds on the entity now, else the first that does not */
    static String assumptionsFail(SleepAnalysis.MobPlan mp, Entity e) {
        for (String k : mp.nulls()) if (value(e, k, true) != null) return "needs null " + k;
        for (String k : mp.zeros()) if (number(e, k) != 0) return "needs zero " + k;
        for (String k : mp.le0()) if (number(e, k) > 0) return "needs <= 0 " + k;
        for (String k : mp.ge0()) if (number(e, k) < 0) return "needs >= 0 " + k;
        for (String k : mp.empty()) if (!empty(value(e, k, false))) return "needs empty " + k;
        for (String k : mp.falseCalls()) if (call(e, k)) return "needs false " + k;
        return null;
    }

    /** the empty collections, checked every replayed tick (their content is not in the reference check) */
    static boolean stillEmpty(SleepAnalysis.MobPlan mp, Entity e) {
        for (String k : mp.empty()) if (!empty(value(e, k, false))) return false;
        return true;
    }

    private static boolean empty(Object v) {
        return v instanceof java.util.Collection<?> c ? c.isEmpty() : v instanceof Map<?, ?> m && m.isEmpty();
    }

    private static Object value(Entity e, String key, boolean nullIfMissing) {
        int dot = key.lastIndexOf('.');
        Object holder = dot < 0 ? e : resolve(e, key.substring(0, dot));
        return holder == null ? null : fieldValue(holder, dot < 0 ? key : key.substring(dot + 1));
    }

    private static long number(Entity e, String key) {
        Object v = value(e, key, false);
        return v instanceof Boolean b ? (b ? 1 : 0) : v instanceof Number n ? n.longValue() : v instanceof Character c ? c : Long.MIN_VALUE;
    }

    /** a boolean method (name, or name#arg) of the object at the key's path */
    private static boolean call(Entity e, String key) {
        int dot = key.lastIndexOf('.');
        Object holder = dot < 0 ? e : resolve(e, key.substring(0, dot));
        String name = dot < 0 ? key : key.substring(dot + 1);
        if (holder == null) return true;
        int hash = name.indexOf('#');
        String mn = hash < 0 ? name : name.substring(0, hash);
        String ck = holder.getClass().getName() + "." + name;
        Method m = METHODS.get(ck);
        if (m == null && !METHODS.containsKey(ck)) {
            for (Class<?> c = holder.getClass(); c != null && c != Object.class && m == null; c = c.getSuperclass())
                for (Method x : c.getDeclaredMethods())
                    if (x.getName().equals(mn) && x.getReturnType() == boolean.class
                            && (hash < 0 ? x.getParameterCount() == 0 : x.getParameterCount() == 1 && x.getParameterTypes()[0] == int.class)) {
                        x.setAccessible(true);
                        m = x;
                        break;
                    }
            METHODS.put(ck, m);
        }
        if (m == null) return true;
        try {
            return (Boolean) (hash < 0 ? m.invoke(holder) : m.invoke(holder, Integer.parseInt(name.substring(hash + 1))));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true;
        }
    }

    // ------------------------------------------------------------------ draws

    static final int NEXT_INT = 0, NEXT_INT_BOUND = 1, NEXT_LONG = 2, NEXT_BOOLEAN = 3, NEXT_FLOAT = 4, NEXT_DOUBLE = 5;

    /** the random being recorded (an observed entity's); null otherwise */
    static Object REC;
    /** the draws of the tick being observed: (site id << 40 | kind << 32 | bound), value bits */
    static final LongArrayList TICK = new LongArrayList();
    private static final Map<String, Integer> SITE_IDS = new HashMap<>();
    static final List<String> SITES = new ArrayList<>();
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set<String> RANDOM_CLASSES = Set.of("net.minecraft.world.level.levelgen.LegacyRandomSource",
            "net.minecraft.world.level.levelgen.BitRandomSource", "net.minecraft.util.RandomSource");

    /** MobSleepRandomMixin: a draw of the random being recorded */
    static void draw(int kind, int bound, long valueBits) {
        String site = WALKER.walk(fs -> fs.filter(f -> !RANDOM_CLASSES.contains(f.getClassName()) && !f.getClassName().equals(MobSleep.class.getName()) && !f.getClassName().equals(MobSleepHooks.class.getName()))
                .findFirst().map(f -> f.getClassName().replace('.', '/') + "." + f.getMethodName() + f.getDescriptor() + "@" + f.getLineNumber()).orElse("?"));
        Integer id = SITE_IDS.get(site);
        if (id == null) {
            id = SITES.size();
            SITES.add(site);
            SITE_IDS.put(site, id);
        }
        TICK.add(((long) id << 40) | ((long) kind << 32) | (bound & 0xFFFFFFFFL));
        TICK.add(valueBits);
    }

    static int siteOf(long key) {
        return (int) (key >>> 40);
    }

    static int kindOf(long key) {
        return (int) (key >>> 32) & 0xFF;
    }

    static int boundOf(long key) {
        return (int) key;
    }

    /** the draw conditions of a class by call site (owner.method+desc@line): a site with two conditions is ambiguous */
    static Map<String, List<Integer>> sitesOf(List<SleepAnalysis.DrawCond> draws) {
        Map<String, List<Integer>> m = new HashMap<>();
        for (int i = 0; i < draws.size(); i++) {
            String s = draws.get(i).site();
            m.computeIfAbsent(s.substring(0, s.lastIndexOf('#')), k -> new ArrayList<>()).add(i);
        }
        return m;
    }

    /** the draw's value as a number (ints and booleans exact, floats as their double) */
    static double asNumber(int kind, long bits) {
        return switch (kind) {
            case NEXT_FLOAT -> Float.intBitsToFloat((int) bits);
            case NEXT_DOUBLE -> Double.longBitsToDouble(bits);
            default -> bits;
        };
    }

    /** whether the condition's jump is taken for this draw value and the other side's value */
    static boolean taken(SleepAnalysis.DrawCond d, double v, double other) {
        double a = d.drawFirst() ? v : other, b = d.drawFirst() ? other : v;
        int c = Double.compare(a, b);
        if (c != 0) c = c < 0 ? -1 : 1;
        int op = d.jumpOp();
        if (op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ICMPLE) op -= Opcodes.IF_ICMPEQ - Opcodes.IFEQ;
        return switch (op) {
            case Opcodes.IFEQ -> c == 0;
            case Opcodes.IFNE -> c != 0;
            case Opcodes.IFLT -> c < 0;
            case Opcodes.IFGE -> c >= 0;
            case Opcodes.IFGT -> c > 0;
            case Opcodes.IFLE -> c <= 0;
            default -> throw new IllegalStateException("jump " + op);
        };
    }

    /** a copy of LegacyRandomSource's generator: the same next(bits) and the same draws as BitRandomSource */
    static final class Lcg {
        long seed;

        Lcg(long seed) {
            this.seed = seed;
        }

        int next(int bits) {
            seed = seed * 25214903917L + 11L & 281474976710655L;
            return (int) (seed >> 48 - bits);
        }

        long draw(int kind, int bound) {
            switch (kind) {
                case NEXT_INT:
                    return next(32);
                case NEXT_INT_BOUND: {
                    if ((bound & bound - 1) == 0) return (int) ((long) bound * (long) next(31) >> 31);
                    int i, j;
                    do {
                        i = next(31);
                        j = i % bound;
                    } while (i - j + (bound - 1) < 0);
                    return j;
                }
                case NEXT_LONG: {
                    int i = next(32), j = next(32);
                    return ((long) i << 32) + j;
                }
                case NEXT_BOOLEAN:
                    return next(1) != 0 ? 1 : 0;
                case NEXT_FLOAT:
                    return Float.floatToRawIntBits(next(24) * 5.9604645E-8F);
                case NEXT_DOUBLE: {
                    int i = next(26), j = next(27);
                    return Double.doubleToRawLongBits((double) (((long) i << 27) + j) * 1.1102230246251565E-16);
                }
                default:
                    throw new IllegalStateException("draw kind " + kind);
            }
        }
    }

    /** the same draw on the real random (the replay): its seed advances as the tick would have advanced it */
    static void replay(net.minecraft.util.RandomSource r, int kind, int bound) {
        switch (kind) {
            case NEXT_INT -> r.nextInt();
            case NEXT_INT_BOUND -> r.nextInt(bound);
            case NEXT_LONG -> r.nextLong();
            case NEXT_BOOLEAN -> r.nextBoolean();
            case NEXT_FLOAT -> r.nextFloat();
            case NEXT_DOUBLE -> r.nextDouble();
            default -> throw new IllegalStateException("draw kind " + kind);
        }
    }

    private static final Field LEGACY_SEED = field(net.minecraft.world.level.levelgen.LegacyRandomSource.class, "seed");

    /** the AtomicLong of a LegacyRandomSource (null for other kinds of random) */
    static AtomicLong seedOf(Object random) {
        if (!(random instanceof net.minecraft.world.level.levelgen.LegacyRandomSource) || LEGACY_SEED == null) return null;
        try {
            return (AtomicLong) LEGACY_SEED.get(random);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
