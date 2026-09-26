package dev.probe.optsupport.sleep;

import dev.probe.optsupport.MixinTouch;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Runtime bytecode analysis of a block-entity class (once per class): which of its fields are clocks and which
 * conditions on them decide when something happens. Port of experiments/be-analysis, returning a structured
 * {@link Plan} instead of text, and deliberately conservative: anything it cannot prove makes the class ineligible.
 */
final class SleepAnalysis {
    static final String BE = "net/minecraft/world/level/block/entity/BlockEntity";
    static final String TICKER_RET = ")Lnet/minecraft/world/level/block/entity/BlockEntityTicker;";
    /** own call graph depth; deeper own calls are refused (their conditions would go unseen) */
    static final int MAX_DEPTH = 64;
    /** Owned-object types whose method calls cannot hide state from the field snapshot (immutable or snapshotted deeply). */
    static final Set<String> SAFE_OWNED = Set.of("net/minecraft/core/NonNullList", "net/minecraft/world/item/ItemStack",
            "net/minecraft/core/Direction", "net/minecraft/core/BlockPos", "net/minecraft/world/level/block/state/BlockState",
            "java/lang/Integer", "java/lang/Boolean", "java/lang/String", "net/minecraft/resources/ResourceKey",
            // memoization caches: their state never changes a result, only how fast it is found
            "net/minecraft/world/item/crafting/RecipeManager$CachedCheck");
    static final Set<String> BASE_FIELDS = Set.of("level", "worldPosition", "blockState", "remove", "components", "hasSavedData");

    /** A condition "clock ? threshold": the engine wakes before its outcome can change. */
    /**
     * clock ? const/field; or gameTime % mod ? const (clock null); or (clock + x) % mod ? y (clock set, mod > 0,
     * {@code clockMod}): a periodic condition on a clock, whose outcome can change every tick.
     */
    record Cond(Field clock, Field thresholdField, long thresholdConst, long mod, String text, boolean clockMod, boolean peer) {
        Cond(Field clock, Field thresholdField, long thresholdConst, long mod, String text) {
            this(clock, thresholdField, thresholdConst, mod, text, false, false);
        }

        Cond(Field clock, Field thresholdField, long thresholdConst, long mod, String text, boolean clockMod) {
            this(clock, thresholdField, thresholdConst, mod, text, clockMod, false);
        }

        /** entity mode: the clock compared with the same field of another instance (a neighbour read by the tick) */
        static Cond peer(Field clock, String text) {
            return new Cond(clock, null, 0, 0, text, false, true);
        }
    }

    /** What the engine may do with a class. */
    /** What the engine may do with a class; {@code watch} = the only fields a tick can change (clocks included). */
    /**
     * {@code nulls} (entity mode): object fields that must be null while asleep. Code that would only run with such a
     * field set (behind a null test on it, or after a call on it) is excluded from the analysis.
     */
    record Plan(boolean eligible, String reason, List<Field> clocks, List<Cond> conds, List<Field> watch, long period, List<Field> nulls) {
        Plan(boolean eligible, String reason, List<Field> clocks, List<Cond> conds) {
            this(eligible, reason, clocks, conds, List.of(), 1, List.of());
        }

        Plan(boolean eligible, String reason, List<Field> clocks, List<Cond> conds, List<Field> watch) {
            this(eligible, reason, clocks, conds, watch, 1, List.of());
        }
    }

    private final Map<String, ClassNode> nodes = new HashMap<>();
    private final ClassLoader loader;
    private final Map<String, Field> clocks = new LinkedHashMap<>();
    private final List<Cond> conds = new ArrayList<>();
    /** fields the followed code writes, plus owned containers whose contents it may change in place */
    private final Map<String, Field> watch = new LinkedHashMap<>();
    private String reject;
    private Set<String> chain;      // the block entity's class chain (internal names), for provenance of values
    private Class<?> beCls;

    /** entity mode: the engine snapshots every field of the entity, so owned objects whose state it sees are allowed */
    private boolean entityMode;
    /** diagnostics (-Dlab.sleep.allReasons): keep going after a refusal and collect every reason */
    private static final boolean ALL_REASONS = Boolean.getBoolean("lab.sleep.allReasons");
    private final java.util.Map<String, Integer> reasons = new java.util.LinkedHashMap<>();

    private boolean ok() {
        return ok(null);
    }

    private boolean ok(String here) {
        if (ALL_REASONS && reject != null) {
            reasons.merge(here == null ? reject : reject + " @" + here, 1, Integer::sum);
            reject = null;
        }
        return reject == null;
    }

    /** entity mode: own object fields assumed null (see Plan.nulls); grown by candidates until a fixpoint */
    private final Set<String> nullFields = new HashSet<>();
    private final Set<String> nullCandidates = new HashSet<>();

    private boolean nullField(Operand o) {
        return entityMode && o != null && o.kind == Kind.FIELD && o.self && chain != null && chain.contains(o.owner) && nullFields.contains(o.name);
    }

    /** Frames plus the instructions that can run under the null assumptions (all reachable ones without assumptions). */
    private record Flow(Frame<SourceValue>[] fr, boolean[] live) {}

    private Flow flow(String owner, MethodNode m) throws AnalyzerException {
        int n = m.instructions.size();
        List<int[]> edges = new ArrayList<>();
        Analyzer<SourceValue> an = new Analyzer<>(new SourceInterpreter()) {
            @Override
            protected void newControlFlowEdge(int insn, int succ) {
                edges.add(new int[] {insn, succ});
            }

            @Override
            protected boolean newControlFlowExceptionEdge(int insn, int succ) {
                edges.add(new int[] {insn, succ, 1});
                return true;
            }
        };
        Frame<SourceValue>[] fr = an.analyze(owner, m);
        boolean[] live = new boolean[n];
        if (nullFields.isEmpty()) {
            for (int i = 0; i < n; i++) live[i] = fr[i] != null;
            return new Flow(fr, live);
        }
        int[] cnt = new int[n + 1];
        for (int[] e : edges) cnt[e[0] + 1]++;
        for (int i = 0; i < n; i++) cnt[i + 1] += cnt[i];
        int[] adj = new int[edges.size()], exc = new int[edges.size()], pos = cnt.clone();
        for (int[] e : edges) {
            exc[pos[e[0]]] = e.length > 2 ? 1 : 0;
            adj[pos[e[0]]++] = e[1];
        }
        AbstractInsnNode[] in = m.instructions.toArray();
        int[] stack = new int[n];
        int sp = 0;
        if (n > 0) {
            live[0] = true;
            stack[sp++] = 0;
        }
        while (sp > 0) {
            int i = stack[--sp];
            int op = in[i].getOpcode();
            int only = -2;   // -2: all normal successors; -1: none; else the only one
            if ((op == Opcodes.IFNULL || op == Opcodes.IFNONNULL) && nullField(operand(fr[i], 1, fr, m, 0))) {
                int target = m.instructions.indexOf(((JumpInsnNode) in[i]).label);
                only = op == Opcodes.IFNULL ? target : i + 1;
            } else if (in[i] instanceof MethodInsnNode mi && op != Opcodes.INVOKESTATIC
                    && nullField(operand(fr[i], Type.getArgumentTypes(mi.desc).length + 1, fr, m, 0))) {
                only = -1;   // a call on a null field: throws
            } else if ((op == Opcodes.GETFIELD || op == Opcodes.ARRAYLENGTH) && nullField(operand(fr[i], 1, fr, m, 0))) {
                only = -1;
            } else if (op == Opcodes.PUTFIELD && nullField(operand(fr[i], 2, fr, m, 0))) {
                only = -1;
            }
            for (int k = cnt[i]; k < cnt[i + 1]; k++) {
                int t = adj[k];
                if (exc[k] == 0 && only != -2 && t != only) continue;
                if (!live[t] && fr[t] != null) {
                    live[t] = true;
                    stack[sp++] = t;
                }
            }
        }
        return new Flow(fr, live);
    }

    private SleepAnalysis(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Entity classes: the root is the class's tick(); Entity.tickCount is a clock advanced by the level's tick wrapper.
     * The plan's period is the lcm of all clock moduli (1 if none).
     */
    static Plan analyzeEntity(Class<?> entityClass) {
        SleepAnalysis a = new SleepAnalysis(entityClass.getClassLoader());
        a.entityMode = true;
        try {
            return a.runEntity(entityClass);
        } catch (Exception | LinkageError e) {
            return new Plan(false, "analysis failed: " + e, List.of(), List.of());
        }
    }

    private Plan runEntity(Class<?> ec) throws AnalyzerException {
        Set<String> chainSet = new HashSet<>();
        for (Class<?> c = ec; c != null && c != Object.class; c = c.getSuperclass()) chainSet.add(internal(c));
        this.chain = chainSet;
        this.beCls = ec;
        Field tickCount = field(ec, "tickCount");
        if (tickCount == null) return new Plan(false, "no tickCount", List.of(), List.of());
        clocks.put(fkey("net/minecraft/world/entity/Entity", "tickCount"), tickCount);
        String decl = ownerOf(internal(ec), "tick", "()V");
        MethodNode root = decl == null ? null : method(decl, "tick", "()V");
        if (root == null) return new Plan(false, reject != null ? reject : "tick() not found", List.of(), List.of());
        Set<String> seen = new HashSet<>();
        for (int round = 0; ; round++) {
            clocks.clear();
            clocks.put(fkey("net/minecraft/world/entity/Entity", "tickCount"), tickCount);
            conds.clear();
            watch.clear();
            moduli.clear();
            reasons.clear();
            reject = null;
            nullCandidates.clear();
            seen.clear();
            follow(decl, root, 0, seen, chainSet, ec);
            if (ok()) {
                seen.clear();
                branches(decl, root, 0, seen, chainSet, ec);
            }
            if (nullFields.containsAll(nullCandidates) || round == 8) break;
            nullFields.addAll(nullCandidates);
        }
        ok();
        if (!reasons.isEmpty()) reject = reasons.size() + " reasons (null: " + nullFields + "): " + String.join(" | ", reasons.keySet());
        if (reject != null) return new Plan(false, reject, List.copyOf(clocks.values()), List.copyOf(conds));
        long period = 1;
        for (long n : moduli) {
            period = lcm(period, n);
            if (period > 1_000_000) return new Plan(false, "period too long", List.copyOf(clocks.values()), List.copyOf(conds));
        }
        List<Field> nulls = new ArrayList<>();
        for (String f : nullFields) {
            Field fld = field(ec, f);
            if (fld == null) return new Plan(false, "field not accessible: " + f, List.of(), List.of());
            nulls.add(fld);
        }
        return new Plan(true, "ok: period " + period + (nulls.isEmpty() ? "" : ", null: " + nullFields), List.copyOf(clocks.values()), List.copyOf(conds),
                List.copyOf(watch.values()), period, List.copyOf(nulls));
    }

    private static long lcm(long a, long b) {
        long x = a, y = b;
        while (y != 0) {
            long t = x % y;
            x = y;
            y = t;
        }
        return a / x * b;
    }

    /** Owned objects whose whole state the entity snapshot or the engine's runtime checks see. */
    private static final Set<String> ENTITY_SAFE = Set.of("net/minecraft/network/syncher/SynchedEntityData", "net/minecraft/util/RandomSource",
            "net/minecraft/world/level/levelgen/LegacyRandomSource", "net/minecraft/world/level/levelgen/SingleThreadedRandomSource",
            "net/minecraft/world/phys/Vec3", "net/minecraft/world/phys/AABB", "java/util/Optional", "java/util/stream/Stream",
            "net/minecraft/world/entity/EntityDimensions", "net/minecraft/core/Rotations", "net/minecraft/world/damagesource/DamageSources",
            "net/minecraft/network/chat/Component", "net/minecraft/world/item/alchemy/PotionContents",
            // section bookkeeping keyed by position: a no-op while the position does not change (the engine requires that)
            "net/minecraft/world/level/entity/EntityInLevelCallback");

    static Plan analyze(Class<? extends BlockEntity> beClass, Class<?> blockClass) {
        SleepAnalysis a = new SleepAnalysis(beClass.getClassLoader());
        try {
            return a.run(beClass, blockClass);
        } catch (Exception | LinkageError e) {
            return new Plan(false, "analysis failed: " + e, List.of(), List.of());
        }
    }

    private Plan run(Class<?> beClass, Class<?> blockClass) throws AnalyzerException {
        Set<String> beChain = new HashSet<>();
        for (Class<?> c = beClass; c != null && c != Object.class; c = c.getSuperclass()) beChain.add(internal(c));
        this.chain = beChain;
        this.beCls = beClass;
        // ticker candidates: lambdas producing a BlockEntityTicker in the block class chain whose 4th parameter fits this BE
        List<String[]> tickers = new ArrayList<>();
        for (Class<?> c = blockClass; c != null && c != Object.class; c = c.getSuperclass()) {
            ClassNode cn = node(internal(c));
            if (cn == null) continue;
            if (MixinTouch.touched(internal(c), "getTicker"))
                return new Plan(false, "mixin-changed " + c.getSimpleName() + ".getTicker", List.of(), List.of());
            for (MethodNode m : cn.methods) {
                for (AbstractInsnNode in : m.instructions) {
                    if (!(in instanceof InvokeDynamicInsnNode indy) || !indy.desc.endsWith(TICKER_RET) || indy.bsmArgs.length < 2
                            || !(indy.bsmArgs[1] instanceof Handle h)) continue;
                    Type[] args = Type.getArgumentTypes(h.getDesc());
                    Type beArg = args.length == 4 ? args[3] : null;
                    if (beArg != null && beChain.contains(beArg.getInternalName())) {
                        if (MixinTouch.touched(internal(c), m.name))
                            return new Plan(false, "mixin-changed " + c.getSimpleName() + "." + m.name, List.of(), List.of());
                        tickers.add(new String[] {h.getOwner(), h.getName(), h.getDesc()});
                    }
                }
            }
        }
        if (tickers.isEmpty()) return new Plan(false, "no ticker found in " + blockClass.getSimpleName(), List.of(), List.of());
        Set<String> seen = new HashSet<>();
        for (String[] t : tickers) {
            MethodNode m = method(t[0], t[1], t[2]);
            if (m == null) return new Plan(false, "ticker method not found " + t[1], List.of(), List.of());
            follow(t[0], m, 0, seen, beChain, beClass);
            if (reject != null) break;
        }
        // conditions are gathered in a second pass, once all clocks are known
        if (reject == null) {
            seen.clear();
            for (String[] t : tickers) branches(t[0], method(t[0], t[1], t[2]), 0, seen, beChain, beClass);
        }
        if (reject != null) return new Plan(false, reject, List.copyOf(clocks.values()), List.copyOf(conds));
        for (Field c : clocks.values()) if (c != null) watch.putIfAbsent(c.getName(), c);
        return new Plan(true, "ok: " + tickers.size() + " ticker(s), watching " + watch.size() + " field(s)", List.copyOf(clocks.values()),
                List.copyOf(conds), List.copyOf(watch.values()));
    }

    // ---------------------------------------------------------------- pass 1: clocks and hidden state

    private void follow(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass) throws AnalyzerException {
        follow(owner, m, depth, seen, beChain, beClass, (m.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);
    }

    private void follow(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass, int self) throws AnalyzerException {
        if (!ok() || !seen.add(owner + "." + m.name + m.desc + (entityMode ? "#" + self : ""))) return;
        int savedSelf = selfMask;
        selfMask = self;
        try {
            follow0(owner, m, depth, seen, beChain, beClass);
        } finally {
            selfMask = savedSelf;
        }
    }

    private void follow0(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass) throws AnalyzerException {
        if (MixinTouch.touched(owner, m.name)) {   // the resource bytes are not the code that runs
            reject = "mixin-changed " + simple(owner) + "." + m.name;
            return;
        }
        Flow flow = flow(owner, m);
        Frame<SourceValue>[] fr = flow.fr();
        String here = simple(owner) + "." + m.name;
        AbstractInsnNode[] in = m.instructions.toArray();
        for (int i = 0; i < in.length && ok(here); i++) {
            if (fr[i] == null || !flow.live()[i]) continue;
            int op = in[i].getOpcode();
            if (in[i] instanceof InvokeDynamicInsnNode indy) {
                lambda(indy, fr, i, m, depth, seen, beChain, beClass, true);
                continue;
            }
            if ((op == Opcodes.PUTFIELD || op == Opcodes.GETFIELD) && in[i] instanceof FieldInsnNode wf && beChain.contains(wf.owner)
                    && !BASE_FIELDS.contains(wf.name) && (op == Opcodes.PUTFIELD || containerType(wf.desc))) {
                Field fld = field(beClass, wf.name);
                if (fld == null) { reject = "field not accessible: " + wf.name; break; }
                watch.putIfAbsent(wf.name, fld);
            }
            if (entityMode && op == Opcodes.PUTFIELD && in[i] instanceof FieldInsnNode pf && !onThis(fr[i], 2, fr, m) && !entityClass(pf.owner)
                    && !fresh(fr[i], 2, fr, m, 0) && !localScratch(pf.owner, owner, m)) {
                reject = "writes " + simple(pf.owner) + "." + pf.name + " of a foreign object";
                break;
            }
            if (op == Opcodes.PUTFIELD && in[i] instanceof FieldInsnNode f && beChain.contains(f.owner) && (!entityMode || onThis(fr[i], 2, fr, m))) {
                String kind = clockKind(f, fr, m, i);
                if (kind != null) clocks.putIfAbsent(fkey(f.owner, f.name), field(beClass, f.name));
                if ("timestamp".equals(kind)) stamps.add(f.name);
            } else if (op >= Opcodes.IASTORE && op <= Opcodes.SASTORE) {
                Operand arr = operand(fr[i], 3, fr, m, 0);
                if (arr.kind == Kind.FIELD && beChain.contains(arr.owner) && !BASE_FIELDS.contains(arr.name)) {
                    Field af = field(beClass, arr.name);   // snapshotted by content (FieldDiff), so the write is visible
                    if (af == null) reject = "field not accessible: " + arr.name;
                    else watch.putIfAbsent(arr.name, af);
                } else if (arr.kind == Kind.OWNCALL) {
                    reject = "writes an array owned by the block entity that the snapshot cannot see (" + arr.name + ")";
                }
            } else if (in[i] instanceof MethodInsnNode mi) {
                // game time handed to a call: the comparison would happen where the analysis cannot see it
                int argc = Type.getArgumentTypes(mi.desc).length;
                for (int k = 1; k <= argc && reject == null; k++)
                    if (containsGameTime(operand(fr[i], k, fr, m, 0))) reject = "game time passed to " + simple(mi.owner) + "." + mi.name;
                if (reject != null) break;
                if (beChain.contains(mi.owner) && depth >= MAX_DEPTH) {
                    reject = "own call graph deeper than " + MAX_DEPTH + " (" + mi.name + ")";
                    break;
                }
                if (depth < MAX_DEPTH && beChain.contains(mi.owner)) {
                    String decl = mi.getOpcode() == Opcodes.INVOKESTATIC || mi.getOpcode() == Opcodes.INVOKESPECIAL ? ownerOf(mi.owner, mi.name, mi.desc) : ownerOf(internal(beClass), mi.name, mi.desc);
                    MethodNode target = decl == null ? null : method(decl, mi.name, mi.desc);
                    if (target != null && (target.access & Opcodes.ACC_ABSTRACT) == 0) follow(decl, target, depth + 1, seen, beChain, beClass, calleeMask(fr[i], mi, fr, m));
                    else if (reject == null) reject = "unresolved own method " + mi.name;
                    continue;
                }
                // a call on (or with) an object owned by the block entity may change hidden state
                // instance calls: only the receiver matters (arguments like a RecipeType are registry objects);
                // static calls: any owned argument could be mutated by the helper
                // receiver and every argument; registry objects (the RecipeType of a furnace) are shared constants, not state
                int nargs = Type.getArgumentTypes(mi.desc).length + (op == Opcodes.INVOKESTATIC ? 0 : 1);
                if (entityMode && ENTITY_SINKS.contains(mi.owner + "." + mi.name)) continue;
                for (int k = 0; k < nargs; k++) {
                    Operand o = operand(fr[i], nargs - k, fr, m, 0);
                    if (nullField(o)) continue;   // null here: nothing to call or to change
                    if (isOwnField(o, beChain) && !safeOwned(o.type) && !registryType(o.type) && !pureReader(mi)
                            && !(entityMode && o.type != null && ENTITY_SAFE.contains(o.type))) {
                        if (entityMode && o.kind == Kind.FIELD && o.type != null && o.type.startsWith("[") && mi.owner.equals("java/util/Arrays")) {
                            Field af = field(beClass, o.name);   // an own array changed in place: snapshotted by content
                            if (af != null) { watch.putIfAbsent(o.name, af); continue; }
                        }
                        if (entityMode && o.kind == Kind.FIELD && o.type != null && o.type.length() > 1) {
                            nullCandidates.add(o.name);   // assume it null (checked at run time) and look again
                            continue;
                        }
                        reject = "calls " + simple(mi.owner) + "." + mi.name + (k == 0 && op != Opcodes.INVOKESTATIC ? " on" : " with") + " owned state " + o.name;
                        break;
                    }
                }
                if (true) continue;
                for (int k = 0; k < nargs; k++) {
                    Operand o = operand(fr[i], nargs - k, fr, m, 0);
                    if (isOwnField(o, beChain) && !safeOwned(o.type)) {
                        reject = "calls " + simple(mi.owner) + "." + mi.name + " on owned state " + o.name;
                        break;
                    }
                }
            }
        }
    }

    /** Counter: field = field +- const; timestamp: field = level.getGameTime(). */
    private String clockKind(FieldInsnNode f, Frame<SourceValue>[] fr, MethodNode m, int i) {
        if (entityMode && !onThis(fr[i], 2, fr, m)) return null;
        Operand v = operand(fr[i], 1, fr, m, 0);
        if (v.kind == Kind.GAMETIME) return "timestamp";
        if (v.kind == Kind.ARITH && v.a != null && v.b != null && (v.op == Opcodes.IADD || v.op == Opcodes.ISUB || v.op == Opcodes.LADD || v.op == Opcodes.LSUB)) {
            boolean self = (v.a.kind == Kind.FIELD && v.a.name.equals(f.name) && (!entityMode || v.a.self) && v.b.kind == Kind.CONST)
                    || (v.b.kind == Kind.FIELD && v.b.name.equals(f.name) && (!entityMode || v.b.self) && v.a.kind == Kind.CONST);
            if (self) return "counter";
        }
        return null;
    }

    // ---------------------------------------------------------------- pass 2: conditions on clocks

    private void branches(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass) throws AnalyzerException {
        if (m == null) return;
        branches(owner, m, depth, seen, beChain, beClass, (m.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0);
    }

    private void branches(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass, int self) throws AnalyzerException {
        if (!ok() || m == null || !seen.add(owner + "." + m.name + m.desc + (entityMode ? "#" + self : ""))) return;
        int savedSelf = selfMask;
        selfMask = self;
        try {
            branches0(owner, m, depth, seen, beChain, beClass);
        } finally {
            selfMask = savedSelf;
        }
    }

    private void branches0(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass) throws AnalyzerException {
        Flow flow = flow(owner, m);
        Frame<SourceValue>[] fr = flow.fr();
        String here = simple(owner) + "." + m.name;
        AbstractInsnNode[] in = m.instructions.toArray();
        for (int i = 0; i < in.length && ok(here); i++) {
            if (fr[i] == null || !flow.live()[i]) continue;
            if (in[i] instanceof InvokeDynamicInsnNode indy) {
                lambda(indy, fr, i, m, depth, seen, beChain, beClass, false);
            } else if (in[i] instanceof JumpInsnNode j && j.getOpcode() != Opcodes.GOTO && j.getOpcode() != Opcodes.JSR) {
                int op = j.getOpcode();
                boolean two = op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE;
                Operand x, y;
                if (two) {
                    x = operand(fr[i], 2, fr, m, 0);
                    y = operand(fr[i], 1, fr, m, 0);
                } else {
                    Operand v = operand(fr[i], 1, fr, m, 0);
                    if (v.kind == Kind.CMP && (op == Opcodes.IFEQ || op == Opcodes.IFNE) && staleStamp(v.a, v.b)) {
                        continue;   // a game-time stamp tested for "this tick": false once a quiet tick has passed
                    } else if (v.kind == Kind.CMP) {
                        x = v.a;
                        y = v.b;
                    } else {
                        x = v;
                        y = Operand.constant(0);
                    }
                }
                condition(x, y, beChain, beClass);
            } else if (in[i] instanceof MethodInsnNode mb && mb.getOpcode() == Opcodes.INVOKESTATIC && PURE_BOUNDS.contains(mb.owner + "." + mb.name)
                    && clockArgument(fr, i, mb, m)) {
                boundConditions(fr, i, mb, m, beClass);
            } else if (in[i] instanceof MethodInsnNode mi0 && !(entityMode && CLOCK_SINKS.contains(mi0.owner + "." + mi0.name)) && clockArgument(fr, i, mi0, m)) {
                reject = "clock passed to " + simple(mi0.owner) + "." + mi0.name;
            } else if (in[i] instanceof MethodInsnNode mi && depth < MAX_DEPTH && beChain.contains(mi.owner)) {
                String decl = mi.getOpcode() == Opcodes.INVOKESTATIC || mi.getOpcode() == Opcodes.INVOKESPECIAL ? ownerOf(mi.owner, mi.name, mi.desc) : ownerOf(internal(beClass), mi.name, mi.desc);
                MethodNode target = decl == null ? null : method(decl, mi.name, mi.desc);
                if (target != null && (target.access & Opcodes.ACC_ABSTRACT) == 0) branches(decl, target, depth + 1, seen, beChain, beClass, calleeMask(fr[i], mi, fr, m));
            }
        }
    }

    /**
     * invokedynamic lambdas and method references: an implementation in the own class chain is followed like an own
     * call (whoever receives the function object may call it); owned state captured into it is checked like an argument.
     */
    private void lambda(InvokeDynamicInsnNode indy, Frame<SourceValue>[] fr, int i, MethodNode m, int depth, Set<String> seen, Set<String> beChain,
            Class<?> beClass, boolean pass1) throws AnalyzerException {
        if (!indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
            String b = indy.bsm.getOwner();
            if (pass1 && !b.equals("java/lang/invoke/StringConcatFactory") && !b.equals("java/lang/runtime/SwitchBootstraps") && !b.equals("java/lang/runtime/ObjectMethods"))
                reject = "invokedynamic " + b;
            return;
        }
        if (indy.bsmArgs.length < 2 || !(indy.bsmArgs[1] instanceof Handle h)) { reject = "lambda without implementation handle"; return; }
        if (pass1) {
            int argc = Type.getArgumentTypes(indy.desc).length;
            for (int k = 1; k <= argc; k++) {
                Operand o = operand(fr[i], k, fr, m, 0);
                if (nullField(o)) continue;
                if (isOwnField(o, beChain) && !safeOwned(o.type) && !registryType(o.type) && !(entityMode && o.type != null && ENTITY_SAFE.contains(o.type))) {
                    reject = "captures owned state " + o.name + " in a lambda";
                    return;
                }
            }
        }
        if (!beChain.contains(h.getOwner())) return;   // a foreign implementation: like calling it with the captured values
        String decl = h.getTag() == Opcodes.H_INVOKEVIRTUAL || h.getTag() == Opcodes.H_INVOKEINTERFACE ? ownerOf(internal(beClass), h.getName(), h.getDesc())
                : ownerOf(h.getOwner(), h.getName(), h.getDesc());
        MethodNode target = decl == null ? null : method(decl, h.getName(), h.getDesc());
        if (target == null || (target.access & Opcodes.ACC_ABSTRACT) != 0) {
            if (reject == null) reject = "unresolved lambda " + h.getName();
            return;
        }
        if (depth >= MAX_DEPTH) { reject = "own call graph deeper than " + MAX_DEPTH + " (" + h.getName() + ")"; return; }
        int mask = 0, slot = 0;
        Type[] cap = Type.getArgumentTypes(indy.desc);
        for (int k = 0; k < cap.length; k++) {
            if (onThis(fr[i], cap.length - k, fr, m) && slot < 31) mask |= 1 << slot;
            slot += cap[k].getSize();
        }
        if (pass1) follow(decl, target, depth + 1, seen, beChain, beClass, mask);
        else branches(decl, target, depth + 1, seen, beChain, beClass, mask);
    }

    /** a == b with one side a timestamp clock (stores getGameTime) and the other the game time */
    private boolean staleStamp(Operand a, Operand b) {
        if (!entityMode || a == null || b == null) return false;
        return isClock(a) && b.kind == Kind.GAMETIME && stamps.contains(a.name) || isClock(b) && a.kind == Kind.GAMETIME && stamps.contains(b.name);
    }

    private final Set<String> stamps = new HashSet<>();

    /** calls that only store or print a value (no comparison, no owned state changed) */
    private static final Set<String> CLOCK_SINKS = Set.of("net/minecraft/nbt/CompoundTag.putInt", "net/minecraft/nbt/CompoundTag.putShort",
            "net/minecraft/nbt/CompoundTag.putLong", "net/minecraft/nbt/CompoundTag.putByte");
    private static final Set<String> ENTITY_SINKS = Set.of("java/util/Objects.requireNonNullElse", "com/google/common/base/MoreObjects.firstNonNull",
            "net/minecraft/network/chat/Component.getString");

    /** Pure bound functions: they only compare their arguments. */
    private static final Set<String> PURE_BOUNDS = Set.of("net/minecraft/util/Mth.clamp", "java/lang/Math.max", "java/lang/Math.min");

    /**
     * clamp/max/min(clock + c, bounds...): the function compares clock + c with each bound b, i.e. clock with b - c.
     * Bounds must be constants or the block entity's own fields; anything else is refused.
     */
    private void boundConditions(Frame<SourceValue>[] fr, int i, MethodInsnNode mi, MethodNode m, Class<?> beClass) {
        int argc = Type.getArgumentTypes(mi.desc).length;
        Operand[] a = new Operand[argc];
        for (int k = 0; k < argc; k++) a[k] = operand(fr[i], argc - k, fr, m, 0);
        for (int k = 0; k < argc && reject == null; k++) {
            if (!containsClock(a[k])) continue;
            Operand e = a[k], clockOp;
            long c;
            if (isClock(e)) {
                clockOp = e;
                c = 0;
            } else if (e.kind == Kind.ARITH && (e.op == Opcodes.IADD || e.op == Opcodes.LADD) && isClock(e.a) && e.b != null && e.b.kind == Kind.CONST) {
                clockOp = e.a;
                c = e.b.value;
            } else if (e.kind == Kind.ARITH && (e.op == Opcodes.ISUB || e.op == Opcodes.LSUB) && isClock(e.a) && e.b != null && e.b.kind == Kind.CONST) {
                clockOp = e.a;
                c = -e.b.value;
            } else {
                reject = "clock expression passed to " + simple(mi.owner) + "." + mi.name;
                return;
            }
            Field clock = clocks.get(fkey(clockOp.owner, clockOp.name));
            if (clock == null) { reject = "clock not accessible: " + clockOp.name; return; }
            for (int b = 0; b < argc; b++) {
                if (b == k) continue;
                Operand o = a[b];
                if (containsClock(o)) { reject = "two clocks in " + simple(mi.owner) + "." + mi.name; return; }
                if (peerClock(o) && o.name.equals(clockOp.name) && c == 0) {
                    conds.add(Cond.peer(clock, clockOp.name + " ? other." + o.name + " (" + mi.name + ")"));
                    continue;
                }
                if (entityMode && o.kind == Kind.FIELD && !o.self) { reject = "clock bounded by another object's " + o.name + " in " + mi.name; return; }
                if (o.kind == Kind.CONST) {
                    conds.add(new Cond(clock, null, o.value - c, 0, clockOp.name + " ? " + (o.value - c) + " (" + mi.name + ")"));
                } else if (o.kind == Kind.FIELD && chain.contains(o.owner) && !BASE_FIELDS.contains(o.name)) {
                    Field tf = field(beClass, o.name);
                    if (tf == null) { reject = "field not accessible: " + o.name; return; }
                    conds.add(new Cond(clock, tf, -c, 0, clockOp.name + " ? " + o.name + (c == 0 ? "" : " - " + c) + " (" + mi.name + ")"));
                } else {
                    reject = "clock bounded by an unknown value in " + simple(mi.owner) + "." + mi.name;
                    return;
                }
            }
        }
    }

    private boolean clockArgument(Frame<SourceValue>[] fr, int i, MethodInsnNode mi, MethodNode m) {
        int argc = Type.getArgumentTypes(mi.desc).length;
        for (int k = 1; k <= argc; k++) if (containsClock(operand(fr[i], k, fr, m, 0))) return true;
        return false;
    }

    private void condition(Operand x, Operand y, Set<String> beChain, Class<?> beClass) {
        boolean xc = isClock(x), yc = isClock(y);
        boolean xg = x != null && x.kind == Kind.GAMEMOD, yg = y != null && y.kind == Kind.GAMEMOD;
        if (!xc && !yc && !xg && !yg) {
            if ((x != null && x.kind == Kind.GAMETIME) || (y != null && y.kind == Kind.GAMETIME)) reject = "game time compared without modulo";
            else if (containsGameTime(x) || containsGameTime(y)) reject = "game time in an expression the analysis does not model";
            else if (containsClock(x) || containsClock(y)) clockModulo(containsClock(x) ? x : y, containsClock(x) ? y : x);
            return;   // not a time condition: its inputs are covered by the read set / own state
        }
        if (xg || yg) {
            Operand g = xg ? x : y, other = xg ? y : x;
            if (other == null || other.kind != Kind.CONST) { reject = "game time modulo compared with non-constant"; return; }
            conds.add(new Cond(null, null, other.value, g.value, "gameTime % " + g.value + " ? " + other.value));
            return;
        }
        Operand c = xc ? x : y, t = xc ? y : x;
        Field clock = clocks.get(fkey(c.owner, c.name));
        if (t == null) { reject = "clock compared with unknown"; return; }
        switch (t.kind) {
            case CONST -> {
                if (clock == null) { reject = "clock not accessible: " + c.name; return; }
                conds.add(new Cond(clock, null, t.value, 0, c.name + " ? " + t.value));
            }
            case ARITH -> {   // clock ? field +- const
                Operand fa = t.a, cb = t.b;
                boolean add = t.op == Opcodes.IADD || t.op == Opcodes.LADD, sub = t.op == Opcodes.ISUB || t.op == Opcodes.LSUB;
                if ((add || sub) && fa != null && cb != null && fa.kind == Kind.FIELD && (!entityMode || fa.self) && beChain.contains(fa.owner)
                        && !BASE_FIELDS.contains(fa.name) && !isClock(fa) && cb.kind == Kind.CONST) {
                    Field tf = field(beClass, fa.name);
                    if (tf == null || clock == null) { reject = "field not accessible: " + fa.name; return; }
                    conds.add(new Cond(clock, tf, add ? cb.value : -cb.value, 0, c.name + " ? " + fa.name + (add ? " + " : " - ") + cb.value));
                } else {
                    reject = "clock " + c.name + " compared with ARITH";
                }
            }
            case FIELD -> {
                if (peerClock(t) && t.name.equals(c.name)) {
                    if (clock == null) { reject = "clock not accessible: " + c.name; return; }
                    conds.add(Cond.peer(clock, c.name + " ? other." + t.name));
                    return;
                }
                if (entityMode && !t.self) { reject = "clock compared with another object's " + t.name; return; }
                if (!beChain.contains(t.owner)) { reject = "clock compared with foreign field " + t.name; return; }
                Field tf = field(beClass, t.name);
                if (tf == null || clock == null) { reject = "field not accessible: " + t.name; return; }
                conds.add(new Cond(clock, tf, 0, 0, c.name + " ? " + t.name));
            }
            default -> reject = "clock " + c.name + " compared with " + t.kind;
        }
    }

    /** Read-only JDK helpers: they cannot change what they are given. */
    private static final Set<String> PURE_READERS = Set.of("java/util/Arrays.equals", "java/util/Arrays.hashCode", "java/util/Arrays.deepEquals",
            "java/util/Arrays.deepHashCode", "java/util/Arrays.copyOf", "java/util/Arrays.copyOfRange", "java/util/Arrays.toString",
            "java/util/Arrays.mismatch", "java/util/Arrays.compare", "java/util/Arrays.binarySearch", "java/util/Objects.equals",
            "java/util/Objects.hash", "java/util/Objects.hashCode", "java/util/Objects.isNull", "java/util/Objects.nonNull",
            "java/util/Objects.requireNonNull", "java/lang/System.identityHashCode");

    private static boolean pureReader(MethodInsnNode mi) {
        return mi.getOpcode() == Opcodes.INVOKESTATIC && PURE_READERS.contains(mi.owner + "." + mi.name);
    }

    /** Fields whose referenced object changes in place (inventories, maps, collections, stacks, arrays). */
    private boolean containerType(String desc) {
        if (desc.startsWith("[")) return true;
        if (!desc.startsWith("L")) return false;
        String t = desc.substring(1, desc.length() - 1);
        if (t.equals("net/minecraft/core/NonNullList") || t.equals("net/minecraft/world/item/ItemStack")) return true;
        try {
            Class<?> c = Class.forName(t.replace('/', '.'), false, loader);
            return java.util.Map.class.isAssignableFrom(c) || java.util.Collection.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Owned state whose changes the field snapshot sees: immutable/value types, caches, and collections (content hash). */
    private boolean safeOwned(String type) {
        if (type == null || SAFE_OWNED.contains(type)) return true;
        if (type.length() == 1) return true;   // primitive field: passed by value, cannot be mutated by the callee
        try {
            Class<?> c = Class.forName(type.replace('/', '.'), false, loader);
            if (deeplyImmutable(c, new HashSet<>())) return true;   // nothing to change: the reference is the whole state
            return java.util.Map.class.isAssignableFrom(c) || java.util.Collection.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Every instance field of the class and its superclasses is final and itself deeply immutable (primitives, String,
     * boxed values, such classes). Enums without state, records of values. Final classes only (a subclass could add
     * mutable state), except enums, whose constant bodies are checked the same way.
     */
    private static boolean deeplyImmutable(Class<?> c, Set<Class<?>> seen) {
        if (c.isPrimitive() || c == String.class || c == Integer.class || c == Long.class || c == Boolean.class || c == Short.class
                || c == Byte.class || c == Character.class || c == Float.class || c == Double.class) return true;
        if (c.isArray() || c.isInterface()) return false;
        if (!seen.add(c)) return true;
        if (!Modifier.isFinal(c.getModifiers()) && !c.isEnum()) return false;
        if (c.isEnum()) for (Object k : c.getEnumConstants()) if (k.getClass() != c && !fieldsImmutable(k.getClass(), seen)) return false;
        return fieldsImmutable(c, seen);
    }

    private static boolean fieldsImmutable(Class<?> c, Set<Class<?>> seen) {
        for (Class<?> k = c; k != null && k != Object.class && k != Enum.class && k != Record.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!Modifier.isFinal(f.getModifiers()) || !deeplyImmutable(f.getType(), seen)) return false;
            }
        }
        return true;
    }

    /** Moduli of periodic clock conditions ((clock + x) % N ? y), for the entity engine's period. */
    final java.util.TreeSet<Long> moduli = new java.util.TreeSet<>();

    /** (clock +- x) % N ? y with N constant(s) and x, y free of clocks: a periodic condition; anything else is refused. */
    private void clockModulo(Operand e, Operand other) {
        if (containsClock(other) || e == null || e.kind != Kind.ARITH || (e.op != Opcodes.IREM && e.op != Opcodes.LREM)) {
            reject = "clock in an expression the analysis does not model";
            return;
        }
        Operand base = e.a, mod = e.b;
        if (containsClock(mod)) { reject = "clock as a modulus"; return; }
        long[] ns = mod.kind == Kind.CONST ? new long[] {mod.value} : mod.kind == Kind.CONSTSET ? mod.values : null;
        if (ns == null) { reject = "clock modulo a non-constant"; return; }
        Operand clockOp = base;
        if (!isClock(base)) {   // clock + x / x + clock / clock - x
            if (base.kind == Kind.ARITH && (base.op == Opcodes.IADD || base.op == Opcodes.LADD || base.op == Opcodes.ISUB || base.op == Opcodes.LSUB)
                    && (isClock(base.a) && !containsClock(base.b) || isClock(base.b) && !containsClock(base.a) && base.op != Opcodes.ISUB && base.op != Opcodes.LSUB))
                clockOp = isClock(base.a) ? base.a : base.b;
            else { reject = "clock in an expression the analysis does not model"; return; }
        }
        Field clock = clocks.get(fkey(clockOp.owner, clockOp.name));
        if (clock == null) { reject = "clock not accessible: " + clockOp.name; return; }
        for (long n : ns) {
            if (n <= 0) { reject = "clock modulo " + n; return; }
            moduli.add(n);
            conds.add(new Cond(clock, null, 0, n, "(" + clockOp.name + " + ..) % " + n, true));
        }
    }

    /** A local class declared in this very method (its instances are the method's scratch objects), not held by any own field. */
    private boolean localScratch(String cls, String owner, MethodNode m) {
        ClassNode cn = node(cls);
        if (cn == null || !owner.equals(cn.outerClass) || cn.outerMethod == null) return false;
        boolean here = m.name.equals(cn.outerMethod) && m.desc.equals(cn.outerMethodDesc) || m.name.startsWith("lambda$" + cn.outerMethod + "$");
        if (!here) return false;
        for (String c : chain) {
            ClassNode k = node(c);
            if (k != null) for (FieldNode f : k.fields) if (f.desc.contains("L" + cls + ";")) return false;
        }
        return true;
    }

    /** Field key by declaring class: javac names the receiver's static type as owner (ItemEntity.tickCount = Entity.tickCount). */
    private String fkey(String owner, String name) {
        if (owner != null && chain != null && chain.contains(owner) && beCls != null) {
            Field f = field(beCls, name);
            if (f != null) return internal(f.getDeclaringClass()) + "." + name;
        }
        return owner + "." + name;
    }

    private boolean entityClass(String internal) {
        try {
            return net.minecraft.world.entity.Entity.class.isAssignableFrom(Class.forName(internal.replace('/', '.'), false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private boolean containsClock(Operand o) {
        if (o == null) return false;
        if (isClock(o)) return true;
        return containsClock(o.a) || containsClock(o.b);
    }

    private boolean isClock(Operand o) {
        return o != null && o.kind == Kind.FIELD && (!entityMode || o.self) && clocks.containsKey(fkey(o.owner, o.name));
    }

    /** entity mode: the same field as a clock, read on another instance */
    private boolean peerClock(Operand o) {
        return entityMode && o != null && o.kind == Kind.FIELD && !o.self && clocks.containsKey(fkey(o.owner, o.name));
    }

    private boolean isOwnField(Operand o, Set<String> beChain) {
        if (entityMode && o != null && o.type != null && o.type.length() > 1 && !o.type.startsWith("[") && entityClass(o.type)) return false;
        return o != null && (o.kind == Kind.OWNCALL || o.kind == Kind.FIELD && (!entityMode || o.self) && beChain.contains(o.owner) && !BASE_FIELDS.contains(o.name));
    }

    private static boolean containsGameTime(Operand o) {
        if (o == null) return false;
        if (o.kind == Kind.GAMETIME || o.kind == Kind.GAMEMOD) return true;
        return containsGameTime(o.a) || containsGameTime(o.b);
    }

    /** Types whose instances are registry entries (shared, immutable in practice): RecipeType, Block, Item, SoundEvent, ... */
    private static final Map<String, Boolean> REGISTRY_TYPE = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean registryType(String type) {
        if (type == null || type.length() == 1) return false;
        return REGISTRY_TYPE.computeIfAbsent(type, t -> {
            try {
                Class<?> c = Class.forName(t.replace('/', '.'), false, loader);
                for (var reg : net.minecraft.core.registries.BuiltInRegistries.REGISTRY) {
                    var it = reg.iterator();
                    if (it.hasNext() && c.isInstance(it.next())) return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        });
    }

    // ---------------------------------------------------------------- operand model

    /** OWNCALL: an object returned by a method of the block entity itself (other than a plain field getter). */
    enum Kind { FIELD, CONST, CONSTSET, GAMETIME, GAMEMOD, ARITH, CMP, CALL, OWNCALL, LOCAL, OTHER }

    static final class Operand {
        Kind kind = Kind.OTHER;
        String owner, name, type;
        long value;
        long[] values;   // CONSTSET: every constant the value can come from
        boolean self;    // FIELD / OWNCALL: read on this object (not on another instance of the class)
        int op;
        Operand a, b;

        static Operand constant(long v) {
            Operand o = new Operand();
            o.kind = Kind.CONST;
            o.value = v;
            return o;
        }
    }

    /** Is the stack value {@code fromTop} at frame f an object allocated in this method (NEW, possibly through locals)? */
    private static boolean fresh(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        if (f == null || f.getStackSize() < fromTop || depth > 6) return false;
        return freshValue(f.getStack(f.getStackSize() - fromTop), frames, m, depth);
    }

    private static boolean freshValue(SourceValue v, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        if (v.insns.isEmpty() || depth > 6) return false;
        for (AbstractInsnNode src : v.insns) {
            int op = src.getOpcode();
            Frame<SourceValue> sf = frames[m.instructions.indexOf(src)];
            if (op == Opcodes.NEW) continue;
            if (op == Opcodes.DUP && fresh(sf, 1, frames, m, depth + 1)) continue;
            if (src instanceof VarInsnNode vi && op == Opcodes.ALOAD && sf != null) {
                SourceValue lv = sf.getLocal(vi.var);
                boolean ok = !lv.insns.isEmpty();
                for (AbstractInsnNode st : lv.insns) {
                    Frame<SourceValue> stf = frames[m.instructions.indexOf(st)];
                    if (!(st instanceof VarInsnNode) || st.getOpcode() != Opcodes.ASTORE || !fresh(stf, 1, frames, m, depth + 1)) { ok = false; break; }
                }
                if (ok) continue;
            }
            return false;
        }
        return true;
    }

    /** Is the stack value {@code fromTop} at frame f the object itself (ALOAD 0 in an instance method that never reassigns it)? */
    /** local slots holding the object itself in the method being analysed (slot 0 of instance methods, or params given this) */
    private int selfMask = 1;

    private int calleeMask(Frame<SourceValue> f, MethodInsnNode mi, Frame<SourceValue>[] frames, MethodNode m) {
        Type[] args = Type.getArgumentTypes(mi.desc);
        boolean inst = mi.getOpcode() != Opcodes.INVOKESTATIC;
        int n = args.length + (inst ? 1 : 0), mask = 0, slot = 0;
        for (int k = 0; k < n; k++) {
            if (onThis(f, n - k, frames, m) && slot < 31) mask |= 1 << slot;
            slot += inst && k == 0 ? 1 : args[k - (inst ? 1 : 0)].getSize();
        }
        return mask;
    }

    private boolean onThis(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] frames, MethodNode m) {
        if (f == null || f.getStackSize() < fromTop) return false;
        SourceValue v = f.getStack(f.getStackSize() - fromTop);
        for (int hops = 0; hops < 4 && v.insns.size() == 1 && v.insns.iterator().next().getOpcode() == Opcodes.DUP; hops++) {
            // DUP's copy: the value it duplicated (top of the stack at the DUP)
            AbstractInsnNode d = v.insns.iterator().next();
            Frame<SourceValue> df = frames[m.instructions.indexOf(d)];
            if (df == null || df.getStackSize() < 1) return false;
            v = df.getStack(df.getStackSize() - 1);
        }
        if (v.insns.size() != 1 || !(v.insns.iterator().next() instanceof VarInsnNode vi) || vi.getOpcode() != Opcodes.ALOAD || vi.var >= 31
                || (selfMask & (1 << vi.var)) == 0) return false;
        for (AbstractInsnNode x : m.instructions) if (x instanceof VarInsnNode st && st.getOpcode() == Opcodes.ASTORE && st.var == vi.var) return false;
        return true;
    }

    /** The value {@code fromTop} positions from the top of the stack at a frame (1 = top). */
    private Operand operand(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        if (f == null || f.getStackSize() < fromTop) return new Operand();
        return describe(f.getStack(f.getStackSize() - fromTop), frames, m, depth);
    }

    private Operand describe(SourceValue v, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        Operand o = new Operand();
        if (v.insns.size() > 1 && depth <= 4) {
            long[] vals = new long[v.insns.size()];
            int n = 0;
            for (AbstractInsnNode src : v.insns) {
                Operand c = describe(new SourceValue(v.size, src), frames, m, depth + 1);
                if (c.kind != Kind.CONST) return o;
                vals[n++] = c.value;
            }
            o.kind = Kind.CONSTSET;
            o.values = vals;
            return o;
        }
        if (v.insns.size() != 1 || depth > 4) return o;
        AbstractInsnNode src = v.insns.iterator().next();
        int op = src.getOpcode();
        Frame<SourceValue> f = frames[m.instructions.indexOf(src)];
        if (src instanceof FieldInsnNode fi && op == Opcodes.GETFIELD) {
            o.kind = Kind.FIELD;
            o.owner = fi.owner;
            o.name = fi.name;
            o.self = onThis(f, 1, frames, m);
            o.type = Type.getType(fi.desc).getSort() == Type.OBJECT ? Type.getType(fi.desc).getInternalName() : fi.desc;
        } else if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
            o = Operand.constant(op - Opcodes.ICONST_0);
        } else if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) {
            o = Operand.constant(op - Opcodes.LCONST_0);
        } else if (src instanceof IntInsnNode ii && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) {
            o = Operand.constant(ii.operand);
        } else if (src instanceof LdcInsnNode l && (l.cst instanceof Integer || l.cst instanceof Long)) {
            o = Operand.constant(((Number) l.cst).longValue());
        } else if (src instanceof MethodInsnNode mi) {
            if (mi.name.equals("getGameTime") && mi.desc.equals("()J")) o.kind = Kind.GAMETIME;
            else if (chain != null && chain.contains(mi.owner) && op != Opcodes.INVOKESTATIC && Type.getReturnType(mi.desc).getSort() >= Type.ARRAY
                    && (!entityMode || onThis(f, Type.getArgumentTypes(mi.desc).length + 1, frames, m))) {
                // an object handed out by the block entity itself: a plain getter is that field, anything else is owned
                String decl = ownerOf(internal(beCls), mi.name, mi.desc);
                MethodNode t = decl == null ? null : method(decl, mi.name, mi.desc);
                FieldInsnNode got = t == null ? null : getterField(t);
                Type rt = Type.getReturnType(mi.desc);
                if (got != null) {
                    o.kind = Kind.FIELD;
                    o.owner = got.owner;
                    o.name = got.name;
                    o.self = true;
                } else if (entityMode && t != null && handsOutNothingOwned(decl, t)) {
                    o.kind = Kind.CALL;
                    o.name = mi.owner + "." + mi.name;
                } else {
                    o.kind = Kind.OWNCALL;
                    o.name = "result of " + mi.name;
                }
                o.type = rt.getSort() == Type.OBJECT ? rt.getInternalName() : rt.getDescriptor();
            } else {
                o.kind = Kind.CALL;
                o.name = mi.owner + "." + mi.name;
            }
        } else if (src instanceof VarInsnNode vi && (op == Opcodes.ALOAD || op == Opcodes.LLOAD || op == Opcodes.ILOAD)) {
            // follow the local back to what was stored in it (any owned store makes it owned)
            SourceValue lv = f == null ? null : f.getLocal(vi.var);
            o.kind = Kind.LOCAL;
            if (lv != null) {
                for (AbstractInsnNode st : lv.insns) {
                    if (!(st instanceof VarInsnNode) || st.getOpcode() < Opcodes.ISTORE || st.getOpcode() > Opcodes.ASTORE) continue;
                    Frame<SourceValue> sf = frames[m.instructions.indexOf(st)];
                    Operand stored = sf == null ? null : operand(sf, 1, frames, m, depth + 1);
                    if (stored != null && (stored.kind == Kind.OWNCALL || stored.kind == Kind.FIELD || containsGameTime(stored)
                            || stored.kind == Kind.CONST || stored.kind == Kind.CONSTSET || containsClock(stored))) return stored;
                }
            }
        } else if (op == Opcodes.DUP || op == Opcodes.DUP2 || op == Opcodes.DUP_X1 || op == Opcodes.DUP_X2) {
            if (op == Opcodes.DUP2 && f != null && f.getStackSize() >= 2) {
                Operand lower = operand(f, 2, frames, m, depth + 1);
                if (lower.kind == Kind.FIELD) return lower;
            }
            return operand(f, 1, frames, m, depth + 1);
        } else if (op == Opcodes.LCMP || (op >= Opcodes.IADD && op <= Opcodes.LREM)) {
            o.kind = op == Opcodes.LCMP ? Kind.CMP : Kind.ARITH;
            o.op = op;
            o.a = operand(f, 2, frames, m, depth + 1);
            o.b = operand(f, 1, frames, m, depth + 1);
            if ((op == Opcodes.LREM || op == Opcodes.IREM) && o.a.kind == Kind.GAMETIME && o.b.kind == Kind.CONST) {
                Operand g = new Operand();
                g.kind = Kind.GAMEMOD;
                g.value = o.b.value;
                return g;
            }
        } else if (op == Opcodes.I2L || op == Opcodes.L2I || op == Opcodes.I2S || op == Opcodes.I2B || op == Opcodes.I2C) {
            return operand(f, 1, frames, m, depth + 1);
        }
        return o;
    }

    // ---------------------------------------------------------------- class access

    private ClassNode node(String internal) {
        return nodes.computeIfAbsent(internal, k -> {
            try (InputStream in = loader.getResourceAsStream(k + ".class")) {
                if (in == null) return null;
                ClassNode cn = new ClassNode();
                new ClassReader(in).accept(cn, ClassReader.SKIP_FRAMES);
                return cn;
            } catch (Exception e) {
                return null;
            }
        });
    }

    private final Map<String, Boolean> nothingOwned = new HashMap<>();

    /**
     * Every value the method returns is a new object, null, a static field, a constant or the result of a call outside
     * the class chain (whose result the analysis already treats as foreign): the caller gets nothing the object owns.
     */
    private boolean handsOutNothingOwned(String owner, MethodNode t) {
        String key = owner + "." + t.name + t.desc;
        Boolean b = nothingOwned.get(key);
        if (b != null) return b;
        nothingOwned.put(key, false);   // recursion: conservative
        boolean ok;
        try {
            Frame<SourceValue>[] fr = new Analyzer<>(new SourceInterpreter()).analyze(owner, t);
            ok = true;
            AbstractInsnNode[] in = t.instructions.toArray();
            for (int i = 0; i < in.length && ok; i++) {
                if (in[i].getOpcode() != Opcodes.ARETURN || fr[i] == null) continue;
                SourceValue v = fr[i].getStack(fr[i].getStackSize() - 1);
                ok = foreignValue(v, fr, t, 0);
            }
        } catch (AnalyzerException | RuntimeException e) {
            ok = false;
        }
        nothingOwned.put(key, ok);
        return ok;
    }

    private boolean foreignValue(SourceValue v, Frame<SourceValue>[] fr, MethodNode t, int depth) {
        if (v.insns.isEmpty() || depth > 6) return false;
        for (AbstractInsnNode src : v.insns) {
            int op = src.getOpcode();
            if (op == Opcodes.NEW || op == Opcodes.ACONST_NULL || op == Opcodes.GETSTATIC || op == Opcodes.LDC) continue;
            if (src instanceof MethodInsnNode mi && !chain.contains(mi.owner)) continue;
            if (op == Opcodes.CHECKCAST || op == Opcodes.DUP) {
                Frame<SourceValue> f = fr[t.instructions.indexOf(src)];
                if (f != null && foreignValue(f.getStack(f.getStackSize() - 1), fr, t, depth + 1)) continue;
            }
            if (src instanceof VarInsnNode vi && op == Opcodes.ALOAD) {
                Frame<SourceValue> f = fr[t.instructions.indexOf(src)];
                if (f != null && !(vi.var == 0 && (t.access & Opcodes.ACC_STATIC) == 0)) {
                    SourceValue lv = f.getLocal(vi.var);
                    boolean ok = !lv.insns.isEmpty();
                    for (AbstractInsnNode st : lv.insns) {
                        Frame<SourceValue> sf = fr[t.instructions.indexOf(st)];
                        if (!(st instanceof VarInsnNode) || sf == null || !foreignValue(sf.getStack(sf.getStackSize() - 1), fr, t, depth + 1)) { ok = false; break; }
                    }
                    if (ok) continue;
                }
            }
            return false;
        }
        return true;
    }

    /** ALOAD 0; GETFIELD f; xRETURN (ignoring labels and line numbers) -> f */
    private static FieldInsnNode getterField(MethodNode t) {
        AbstractInsnNode[] real = java.util.Arrays.stream(t.instructions.toArray()).filter(x -> x.getOpcode() >= 0).toArray(AbstractInsnNode[]::new);
        if (real.length == 3 && real[0] instanceof VarInsnNode a && a.getOpcode() == Opcodes.ALOAD && a.var == 0
                && real[1] instanceof FieldInsnNode g && g.getOpcode() == Opcodes.GETFIELD && real[2].getOpcode() >= Opcodes.IRETURN && real[2].getOpcode() <= Opcodes.ARETURN)
            return g;
        return null;
    }

    private MethodNode method(String owner, String name, String desc) {
        ClassNode cn = node(owner);
        if (cn == null) return null;
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    private MethodNode resolve(String owner, String name, String desc) {
        String o = ownerOf(owner, name, desc);
        return o == null ? null : method(o, name, desc);
    }

    /** Declaring class of a method: superclass chain first, then (default methods) all interfaces. */
    private String ownerOf(String owner, String name, String desc) {
        List<String> ifaces = new ArrayList<>();
        for (String c = owner; c != null; ) {
            if (MixinTouch.touched(c, name)) {   // a mixin may add or change this method here: resolution unknown
                if (reject == null) reject = "mixin-changed " + simple(c) + "." + name;
                return null;
            }
            MethodNode found = method(c, name, desc);
            if (found != null && (found.access & Opcodes.ACC_ABSTRACT) == 0) return c;
            ClassNode cn = node(c);
            if (cn == null) break;
            ifaces.addAll(cn.interfaces);
            c = cn.superName;
        }
        Set<String> seen = new HashSet<>();
        while (!ifaces.isEmpty()) {
            String i = ifaces.remove(ifaces.size() - 1);
            if (!seen.add(i)) continue;
            if (MixinTouch.touched(i, name)) {
                if (reject == null) reject = "mixin-changed " + simple(i) + "." + name;
                return null;
            }
            MethodNode m = method(i, name, desc);
            if (m != null && (m.access & Opcodes.ACC_ABSTRACT) == 0) return i;
            ClassNode cn = node(i);
            if (cn != null) ifaces.addAll(cn.interfaces);
        }
        return null;
    }

    private static Field field(Class<?> c, String name) {
        for (Class<?> x = c; x != null; x = x.getSuperclass()) {
            try {
                Field f = x.getDeclaredField(name);
                if (Modifier.isStatic(f.getModifiers())) return null;
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    static String internal(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    static String simple(String internal) {
        return internal.substring(internal.lastIndexOf('/') + 1);
    }
}
