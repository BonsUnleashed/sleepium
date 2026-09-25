package dev.probe.optsupport.sleep;

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
    static final int MAX_DEPTH = 3;
    /** Owned-object types whose method calls cannot hide state from the field snapshot (immutable or snapshotted deeply). */
    static final Set<String> SAFE_OWNED = Set.of("net/minecraft/core/NonNullList", "net/minecraft/world/item/ItemStack",
            "net/minecraft/core/Direction", "net/minecraft/core/BlockPos", "net/minecraft/world/level/block/state/BlockState",
            "java/lang/Integer", "java/lang/Boolean", "java/lang/String", "net/minecraft/resources/ResourceKey",
            // memoization caches: their state never changes a result, only how fast it is found
            "net/minecraft/world/item/crafting/RecipeManager$CachedCheck");
    static final Set<String> BASE_FIELDS = Set.of("level", "worldPosition", "blockState", "remove", "components", "hasSavedData");

    /** A condition "clock ? threshold": the engine wakes before its outcome can change. */
    record Cond(Field clock, Field thresholdField, long thresholdConst, long mod, String text) {}

    /** What the engine may do with a class. */
    /** What the engine may do with a class; {@code watch} = the only fields a tick can change (clocks included). */
    record Plan(boolean eligible, String reason, List<Field> clocks, List<Cond> conds, List<Field> watch) {
        Plan(boolean eligible, String reason, List<Field> clocks, List<Cond> conds) {
            this(eligible, reason, clocks, conds, List.of());
        }
    }

    private final Map<String, ClassNode> nodes = new HashMap<>();
    private final ClassLoader loader;
    private final Map<String, Field> clocks = new LinkedHashMap<>();
    private final List<Cond> conds = new ArrayList<>();
    /** fields the followed code writes, plus owned containers whose contents it may change in place */
    private final Map<String, Field> watch = new LinkedHashMap<>();
    private String reject;

    private SleepAnalysis(ClassLoader loader) {
        this.loader = loader;
    }

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
        // ticker candidates: lambdas producing a BlockEntityTicker in the block class chain whose 4th parameter fits this BE
        List<String[]> tickers = new ArrayList<>();
        for (Class<?> c = blockClass; c != null && c != Object.class; c = c.getSuperclass()) {
            ClassNode cn = node(internal(c));
            if (cn == null) continue;
            for (MethodNode m : cn.methods) {
                for (AbstractInsnNode in : m.instructions) {
                    if (!(in instanceof InvokeDynamicInsnNode indy) || !indy.desc.endsWith(TICKER_RET) || indy.bsmArgs.length < 2
                            || !(indy.bsmArgs[1] instanceof Handle h)) continue;
                    Type[] args = Type.getArgumentTypes(h.getDesc());
                    Type beArg = args.length == 4 ? args[3] : null;
                    if (beArg != null && beChain.contains(beArg.getInternalName())) tickers.add(new String[] {h.getOwner(), h.getName(), h.getDesc()});
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
        if (reject != null || !seen.add(owner + "." + m.name + m.desc)) return;
        Frame<SourceValue>[] fr = new Analyzer<>(new SourceInterpreter()).analyze(owner, m);
        AbstractInsnNode[] in = m.instructions.toArray();
        for (int i = 0; i < in.length && reject == null; i++) {
            if (fr[i] == null) continue;
            int op = in[i].getOpcode();
            if ((op == Opcodes.PUTFIELD || op == Opcodes.GETFIELD) && in[i] instanceof FieldInsnNode wf && beChain.contains(wf.owner)
                    && !BASE_FIELDS.contains(wf.name) && (op == Opcodes.PUTFIELD || containerType(wf.desc))) {
                Field fld = field(beClass, wf.name);
                if (fld == null) { reject = "field not accessible: " + wf.name; break; }
                watch.putIfAbsent(wf.name, fld);
            }
            if (op == Opcodes.PUTFIELD && in[i] instanceof FieldInsnNode f && beChain.contains(f.owner)) {
                String kind = clockKind(f, fr, m, i);
                if (kind != null) clocks.putIfAbsent(f.owner + "." + f.name, field(beClass, f.name));
            } else if ((op == Opcodes.IASTORE || op == Opcodes.LASTORE || op == Opcodes.AASTORE) && isOwnField(operand(fr[i], 3, fr, m, 0), beChain)) {
                reject = "writes an array owned by the block entity (state not visible to the snapshot)";
            } else if (in[i] instanceof MethodInsnNode mi) {
                if (depth < MAX_DEPTH && beChain.contains(mi.owner)) {
                    String decl = mi.getOpcode() == Opcodes.INVOKESTATIC || mi.getOpcode() == Opcodes.INVOKESPECIAL ? ownerOf(mi.owner, mi.name, mi.desc) : ownerOf(internal(beClass), mi.name, mi.desc);
                    MethodNode target = decl == null ? null : method(decl, mi.name, mi.desc);
                    if (target != null && (target.access & Opcodes.ACC_ABSTRACT) == 0) follow(decl, target, depth + 1, seen, beChain, beClass);
                    else reject = "unresolved own method " + mi.name;
                    continue;
                }
                // a call on (or with) an object owned by the block entity may change hidden state
                // instance calls: only the receiver matters (arguments like a RecipeType are registry objects);
                // static calls: any owned argument could be mutated by the helper
                int nargs = op == Opcodes.INVOKESTATIC ? Type.getArgumentTypes(mi.desc).length : 1;
                int depthOfFirst = op == Opcodes.INVOKESTATIC ? nargs : Type.getArgumentTypes(mi.desc).length + 1;
                for (int k = 0; k < nargs; k++) {
                    Operand o = operand(fr[i], depthOfFirst - k, fr, m, 0);
                    if (isOwnField(o, beChain) && !safeOwned(o.type)) {
                        reject = "calls " + simple(mi.owner) + "." + mi.name + " on owned state " + o.name;
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
        Operand v = operand(fr[i], 1, fr, m, 0);
        if (v.kind == Kind.GAMETIME) return "timestamp";
        if (v.kind == Kind.ARITH && v.a != null && v.b != null && (v.op == Opcodes.IADD || v.op == Opcodes.ISUB || v.op == Opcodes.LADD || v.op == Opcodes.LSUB)) {
            boolean self = (v.a.kind == Kind.FIELD && v.a.name.equals(f.name) && v.b.kind == Kind.CONST)
                    || (v.b.kind == Kind.FIELD && v.b.name.equals(f.name) && v.a.kind == Kind.CONST);
            if (self) return "counter";
        }
        return null;
    }

    // ---------------------------------------------------------------- pass 2: conditions on clocks

    private void branches(String owner, MethodNode m, int depth, Set<String> seen, Set<String> beChain, Class<?> beClass) throws AnalyzerException {
        if (reject != null || m == null || !seen.add(owner + "." + m.name + m.desc)) return;
        Frame<SourceValue>[] fr = new Analyzer<>(new SourceInterpreter()).analyze(owner, m);
        AbstractInsnNode[] in = m.instructions.toArray();
        for (int i = 0; i < in.length && reject == null; i++) {
            if (fr[i] == null) continue;
            if (in[i] instanceof JumpInsnNode j && j.getOpcode() != Opcodes.GOTO && j.getOpcode() != Opcodes.JSR) {
                int op = j.getOpcode();
                boolean two = op >= Opcodes.IF_ICMPEQ && op <= Opcodes.IF_ACMPNE;
                Operand x, y;
                if (two) {
                    x = operand(fr[i], 2, fr, m, 0);
                    y = operand(fr[i], 1, fr, m, 0);
                } else {
                    Operand v = operand(fr[i], 1, fr, m, 0);
                    if (v.kind == Kind.CMP) {
                        x = v.a;
                        y = v.b;
                    } else {
                        x = v;
                        y = Operand.constant(0);
                    }
                }
                condition(x, y, beChain, beClass);
            } else if (in[i] instanceof MethodInsnNode mi && depth < MAX_DEPTH && beChain.contains(mi.owner)) {
                String decl = mi.getOpcode() == Opcodes.INVOKESTATIC || mi.getOpcode() == Opcodes.INVOKESPECIAL ? ownerOf(mi.owner, mi.name, mi.desc) : ownerOf(internal(beClass), mi.name, mi.desc);
                MethodNode target = decl == null ? null : method(decl, mi.name, mi.desc);
                if (target != null && (target.access & Opcodes.ACC_ABSTRACT) == 0) branches(decl, target, depth + 1, seen, beChain, beClass);
            }
        }
    }

    private void condition(Operand x, Operand y, Set<String> beChain, Class<?> beClass) {
        boolean xc = isClock(x), yc = isClock(y);
        boolean xg = x != null && x.kind == Kind.GAMEMOD, yg = y != null && y.kind == Kind.GAMEMOD;
        if (!xc && !yc && !xg && !yg) {
            if ((x != null && x.kind == Kind.GAMETIME) || (y != null && y.kind == Kind.GAMETIME)) reject = "game time compared without modulo";
            return;   // not a time condition: its inputs are covered by the read set / own state
        }
        if (xg || yg) {
            Operand g = xg ? x : y, other = xg ? y : x;
            if (other == null || other.kind != Kind.CONST) { reject = "game time modulo compared with non-constant"; return; }
            conds.add(new Cond(null, null, other.value, g.value, "gameTime % " + g.value + " ? " + other.value));
            return;
        }
        Operand c = xc ? x : y, t = xc ? y : x;
        Field clock = clocks.get(c.owner + "." + c.name);
        if (t == null) { reject = "clock compared with unknown"; return; }
        switch (t.kind) {
            case CONST -> {
                if (clock == null) { reject = "clock not accessible: " + c.name; return; }
                conds.add(new Cond(clock, null, t.value, 0, c.name + " ? " + t.value));
            }
            case FIELD -> {
                if (!beChain.contains(t.owner)) { reject = "clock compared with foreign field " + t.name; return; }
                Field tf = field(beClass, t.name);
                if (tf == null || clock == null) { reject = "field not accessible: " + t.name; return; }
                conds.add(new Cond(clock, tf, 0, 0, c.name + " ? " + t.name));
            }
            default -> reject = "clock " + c.name + " compared with " + t.kind;
        }
    }

    /** Fields whose referenced object changes in place (inventories, maps, collections, stacks). */
    private boolean containerType(String desc) {
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
            return java.util.Map.class.isAssignableFrom(c) || java.util.Collection.class.isAssignableFrom(c);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private boolean isClock(Operand o) {
        return o != null && o.kind == Kind.FIELD && clocks.containsKey(o.owner + "." + o.name);
    }

    private boolean isOwnField(Operand o, Set<String> beChain) {
        return o != null && o.kind == Kind.FIELD && beChain.contains(o.owner) && !BASE_FIELDS.contains(o.name);
    }

    // ---------------------------------------------------------------- operand model

    enum Kind { FIELD, CONST, GAMETIME, GAMEMOD, ARITH, CMP, CALL, LOCAL, OTHER }

    static final class Operand {
        Kind kind = Kind.OTHER;
        String owner, name, type;
        long value;
        int op;
        Operand a, b;

        static Operand constant(long v) {
            Operand o = new Operand();
            o.kind = Kind.CONST;
            o.value = v;
            return o;
        }
    }

    /** The value {@code fromTop} positions from the top of the stack at a frame (1 = top). */
    private Operand operand(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        if (f == null || f.getStackSize() < fromTop) return new Operand();
        return describe(f.getStack(f.getStackSize() - fromTop), frames, m, depth);
    }

    private Operand describe(SourceValue v, Frame<SourceValue>[] frames, MethodNode m, int depth) {
        Operand o = new Operand();
        if (v.insns.size() != 1 || depth > 4) return o;
        AbstractInsnNode src = v.insns.iterator().next();
        int op = src.getOpcode();
        Frame<SourceValue> f = frames[m.instructions.indexOf(src)];
        if (src instanceof FieldInsnNode fi && op == Opcodes.GETFIELD) {
            o.kind = Kind.FIELD;
            o.owner = fi.owner;
            o.name = fi.name;
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
            else {
                o.kind = Kind.CALL;
                o.name = mi.owner + "." + mi.name;
            }
        } else if (src instanceof VarInsnNode) {
            o.kind = Kind.LOCAL;
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
        } else if (op == Opcodes.I2L || op == Opcodes.L2I) {
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
