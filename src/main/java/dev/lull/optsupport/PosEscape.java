package dev.lull.optsupport;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Proves from bytecode that a method never retains a BlockPos parameter: the position is only read (coordinates,
 * arithmetic that returns new objects, equality) or handed to calls that only read it (block state, block entity,
 * fluid and signal lookups) or to methods that pass the same proof. It is never stored in a field, array or
 * collection, returned, captured by a lambda, locked on, cast to MutableBlockPos or given to unknown code. Then the
 * caller may pass a reused MutableBlockPos: nothing can observe that it changes afterwards. Methods changed by a
 * foreign mixin are refused (MixinTouch). Unknown -> not proven (vanilla path).
 */
public final class PosEscape {
    private static final String POS = "net/minecraft/core/BlockPos", VEC3I = "net/minecraft/core/Vec3i", MPOS = "net/minecraft/core/BlockPos$MutableBlockPos";
    private static final int MAX_DEPTH = 12;
    /** Read-only methods of the position itself (they return primitives or new objects). */
    private static final Set<String> POS_READS = Set.of("getX", "getY", "getZ", "get", "relative", "above", "below", "north", "south",
            "east", "west", "offset", "asLong", "immutable", "distSqr", "distToCenterSqr", "distToLowCornerSqr", "distManhattan",
            "closerThan", "closerToCenterThan", "equals", "hashCode", "compareTo", "toShortString", "toString", "subtract", "multiply",
            "atY", "cross", "getCenter", "getBottomCenter", "mutable", "rotate");
    /** World/state lookups that only read the position they are given. */
    private static final Set<String> READERS = Set.of("getBlockState", "getFluidState", "getBlockEntity", "getSignal", "getDirectSignal",
            "hasSignal", "hasNeighborSignal", "getBestNeighborSignal", "getDirectSignalTo", "getControlInputSignal", "isEmptyBlock",
            "isLoaded", "hasChunkAt", "getBrightness", "getRawBrightness", "getMaxLocalRawBrightness", "canSeeSky", "isInWorldBounds",
            "isOutsideBuildHeight");
    private static final Set<String> WORLD_TYPES = Set.of("net/minecraft/world/level/Level", "net/minecraft/server/level/ServerLevel",
            "net/minecraft/world/level/LevelReader", "net/minecraft/world/level/BlockGetter", "net/minecraft/world/level/SignalGetter",
            "net/minecraft/world/level/LevelAccessor", "net/minecraft/world/level/CollisionGetter", "net/minecraft/world/level/BlockAndTintGetter",
            "net/minecraft/world/level/LevelHeightAccessor", "net/minecraft/world/level/CommonLevelAccessor");

    private static final Map<String, ClassNode> NODES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> RESULTS = new ConcurrentHashMap<>();
    /** True when the conductor predicate of every registered block state is proven (set by SignalProof). */
    public static java.util.function.BooleanSupplier allConductorsOk = () -> false;
    private static final String GET_SIGNAL = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I";

    /** last refusal (diagnostics, -Dlab.opt.posLog=true) */
    public static volatile String lastReason;

    private static boolean no(String why) {
        lastReason = why;
        return false;
    }

    private PosEscape() {}

    /** Does the method (resolved from {@code concrete} up, interfaces included) never retain its parameter number {@code param}? */
    public static boolean nonRetaining(ClassLoader loader, String concrete, String name, String desc, int param) {
        String key = concrete + "." + name + desc + "#" + param;
        return RESULTS.computeIfAbsent(key, k -> {
            try {
                Resolved r = resolve(loader, concrete, name, desc);
                return r != null && proven(loader, concrete, r, param, 0, StateCtx.NONE);
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /**
     * Same, for a lambda-produced functional object: every implementation method the host class wires into an
     * invokedynamic that produces {@code iface} must pass, with the position as its parameter at {@code paramFromEnd}
     * counted from the last one (captured values come first).
     */
    public static boolean lambdaHostNonRetaining(ClassLoader loader, Class<?> lambdaClass, String iface, int paramFromEnd, boolean cached, String block) {
        String n = lambdaClass.getName();
        int cut = n.indexOf("$$Lambda");
        String key = "lambda:" + n.substring(0, Math.max(cut, 0)) + ">" + iface + (cached ? "+cached" : "+" + block);
        return RESULTS.computeIfAbsent(key, k -> {
            try {
                if (cut < 0) {   // an ordinary class implementing the interface: analyse its single abstract-method implementation
                    return false;
                }
                String host = n.substring(0, cut).replace('.', '/');
                if (MixinTouch.anyForeign(host)) return false;
                ClassNode cn = node(loader, host);
                if (cn == null) return false;
                List<Handle> impls = new ArrayList<>();
                for (MethodNode m : cn.methods)
                    for (AbstractInsnNode in : m.instructions)
                        if (in instanceof InvokeDynamicInsnNode indy && Type.getReturnType(indy.desc).getInternalName().equals(iface)) {
                            if (indy.bsmArgs.length < 2 || !(indy.bsmArgs[1] instanceof Handle h)) return false;
                            impls.add(h);
                        }
                if (impls.isEmpty()) return false;
                for (Handle h : impls) {
                    Type[] args = Type.getArgumentTypes(h.getDesc());
                    boolean isStatic = h.getTag() == Opcodes.H_INVOKESTATIC;
                    int param = args.length - paramFromEnd;   // index among the declared parameters
                    if (param < 0) return false;
                    Resolved r = resolve(loader, h.getOwner(), h.getName(), h.getDesc());
                    // (state, level, pos): the state is the third parameter from the end
                    StateCtx ctx = args.length >= 3 ? new StateCtx(args.length - 3, false, cached, block) : StateCtx.NONE;
                    if (r == null || !proven(loader, h.getOwner(), r, param, 0, ctx)) return false;
                    if (!isStatic && h.getTag() != Opcodes.H_INVOKEVIRTUAL && h.getTag() != Opcodes.H_INVOKESPECIAL && h.getTag() != Opcodes.H_INVOKEINTERFACE) return false;
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    record Resolved(String owner, MethodNode method) {}

    /**
     * What calls on the analysed method's own BlockState parameter (declared parameter {@code stateParam}) are known not
     * to retain the position, for the concrete state the caller asks about: its conductor predicate is proven, and it
     * has a shape cache (isCollisionShapeFullBlock then never reads the position).
     */
    public record StateCtx(int stateParam, boolean conductorOk, boolean cached, String block) {
        static final StateCtx NONE = new StateCtx(-1, false, false, null);
        /** stateParam THIS: the analysed method is a BlockState method and the state is its receiver */
        static final int THIS = -2;

        public StateCtx(int stateParam, boolean conductorOk, boolean cached) {
            this(stateParam, conductorOk, cached, null);
        }

        StateCtx withParam(int p) {
            return new StateCtx(p, conductorOk, cached, block);
        }
    }

    private static final String BLOCKSTATE = "net/minecraft/world/level/block/state/BlockState";

    /** Local slot of declared parameter {@code param} (0-based, without the receiver). */
    private static int slotOf(MethodNode m, int param) {
        int slot = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        Type[] args = Type.getArgumentTypes(m.desc);
        for (int i = 0; i < param; i++) slot += args[i].getSize();
        return slot;
    }

    /** @param ctx at depth 0: which calls on the method's own state parameter are safe (see StateCtx) */
    private static boolean proven(ClassLoader loader, String concrete, Resolved r, int param, int depth, StateCtx ctx) {
        if (depth > MAX_DEPTH) return no("too deep");
        MethodNode m = r.method();
        if (MixinTouch.touched(r.owner(), m.name)) return no(r.owner() + "." + m.name + " mixin-changed");
        if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return no(m.name + " abstract/native");
        int posSlot = slotOf(m, param);
        for (AbstractInsnNode in : m.instructions)   // the parameter slot is never reassigned: every ALOAD of it is the parameter
            if (in instanceof VarInsnNode v && v.var == posSlot && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) return no(m.name + " reassigns pos");
        Frame<SourceValue>[] fr;
        try {
            fr = new Analyzer<>(new SourceInterpreter()).analyze(r.owner(), m);
        } catch (Exception e) {
            return false;
        }
        AbstractInsnNode[] insns = m.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            Frame<SourceValue> f = fr[i];
            if (f == null) continue;
            AbstractInsnNode in = insns[i];
            int op = in.getOpcode();
            switch (op) {
                case Opcodes.PUTFIELD -> { if (isPos(f, 1, fr, m, posSlot) || isPos(f, 2, fr, m, posSlot)) return no(m.name + " stores pos in a field"); }
                case Opcodes.PUTSTATIC, Opcodes.ARETURN, Opcodes.MONITORENTER, Opcodes.ATHROW -> { if (isPos(f, 1, fr, m, posSlot)) return no(m.name + " stores/returns pos"); }
                case Opcodes.AASTORE -> { if (isPos(f, 1, fr, m, posSlot)) return no(m.name + " stores pos in an array"); }
                case Opcodes.CHECKCAST -> { if (isPos(f, 1, fr, m, posSlot) && ((TypeInsnNode) in).desc.equals(MPOS)) return no(m.name + " casts pos to mutable"); }
                case Opcodes.INVOKEDYNAMIC -> {
                    int n = Type.getArgumentTypes(((InvokeDynamicInsnNode) in).desc).length;
                    for (int k = 1; k <= n; k++) if (isPos(f, k, fr, m, posSlot)) return no(m.name + " captures pos in a lambda");
                }
                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE -> {
                    MethodInsnNode mi = (MethodInsnNode) in;
                    Type[] args = Type.getArgumentTypes(mi.desc);
                    boolean inst = op != Opcodes.INVOKESTATIC;
                    int total = args.length + (inst ? 1 : 0);
                    for (int k = 0; k < total; k++) {
                        int fromTop = total - k;   // k = 0 is the receiver (instance calls)
                        if (!isPos(f, fromTop, fr, m, posSlot)) continue;
                        boolean receiver = inst && k == 0;
                        int argIndex = inst ? k - 1 : k;
                        if (receiver) {
                            if ((mi.owner.equals(POS) || mi.owner.equals(VEC3I) || mi.owner.equals(MPOS)) && POS_READS.contains(mi.name)) continue;
                            return no(m.name + " calls " + mi.owner + "." + mi.name + " on pos");
                        }
                        if (WORLD_TYPES.contains(mi.owner) && READERS.contains(mi.name)) continue;
                        boolean onState = inst && (mi.owner.endsWith("/BlockState") || mi.owner.endsWith("BlockBehaviour$BlockStateBase")
                                || mi.owner.endsWith("/IBlockStateExtension"));
                        int stateSlot = ctx.stateParam() == StateCtx.THIS ? 0 : ctx.stateParam() >= 0 ? slotOf(m, ctx.stateParam()) : -1;
                        if (stateSlot >= 0 && onState && isParam(f, total, fr, m, stateSlot)) {
                            // on the method's own state: known safe for this state
                            if (mi.name.equals("isRedstoneConductor") && ctx.conductorOk() || mi.name.equals("isCollisionShapeFullBlock") && ctx.cached()) continue;
                            // any other state method: the wrapper itself, whose getBlock() is this state's block
                            if (ctx.block() != null) {
                                Resolved w = resolve(loader, BLOCKSTATE, mi.name, mi.desc);
                                if (w == null) return no(m.name + " unresolved state method " + mi.name);
                                if (!proven(loader, BLOCKSTATE, w, argIndex, depth + 1, ctx.withParam(StateCtx.THIS))) return false;
                                continue;
                            }
                        }
                        // inside a state method: this.getBlock().x(this.asState(), ..., pos, ...) is the state's block
                        if (ctx.stateParam() == StateCtx.THIS && ctx.block() != null && inst && isStateBlock(f, total, fr, m)) {
                            Resolved b = resolve(loader, ctx.block(), mi.name, mi.desc);
                            if (b == null) return no(m.name + " unresolved block method " + mi.name);
                            // the block method's state parameter is this state only if it is passed this.asState()
                            boolean sameState = args.length > 0 && isThisState(f, total - 1, fr, m);
                            if (!proven(loader, ctx.block(), b, argIndex, depth + 1, sameState ? ctx.withParam(0) : StateCtx.NONE)) return false;
                            continue;
                        }
                        // inside a state method: this.isRedstoneConductor.test(this.asState(), level, pos)
                        if (ctx.stateParam() == StateCtx.THIS && ctx.conductorOk() && op == Opcodes.INVOKEINTERFACE && mi.name.equals("test")
                                && mi.owner.endsWith("BlockBehaviour$StatePredicate") && isOwnField(f, total, fr, m, "isRedstoneConductor")) continue;
                        // any state's conductor predicate, when all registered ones are proven
                        if (onState && mi.name.equals("isRedstoneConductor") && allConductorsOk.getAsBoolean()) continue;
                        // a method we can resolve: prove it too, for that argument
                        Resolved callee;
                        if (op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKESPECIAL) callee = resolve(loader, mi.owner, mi.name, mi.desc);
                        else if (inst && isParam(f, total, fr, m, 0) && (m.access & Opcodes.ACC_STATIC) == 0) callee = resolve(loader, concrete, mi.name, mi.desc);   // this.m(...)
                        else return no(m.name + " passes pos to " + mi.owner + "." + mi.name);
                        if (callee == null) return no(m.name + " passes pos to unresolved " + mi.owner + "." + mi.name);
                        // a call on this keeps "this is the state" (state methods); anything else loses the state context
                        StateCtx next = ctx.stateParam() == StateCtx.THIS && inst && isParam(f, total, fr, m, 0) && op != Opcodes.INVOKESTATIC ? ctx : StateCtx.NONE;
                        if (!proven(loader, concrete, callee, argIndex, depth + 1, next)) return false;
                    }
                }
                default -> { }
            }
        }
        return true;
    }

    /** Is the stack value {@code fromTop} (1 = top) the tracked position, directly or through DUP/CHECKCAST/locals? */
    private static boolean isPos(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] fr, MethodNode m, int posSlot) {
        if (f.getStackSize() < fromTop) return false;
        return derives(f.getStack(f.getStackSize() - fromTop), fr, m, posSlot, 0);
    }

    /** Receiver is this.getBlock() (in a state method). */
    private static boolean isStateBlock(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] fr, MethodNode m) {
        if (f.getStackSize() < fromTop) return false;
        SourceValue v = f.getStack(f.getStackSize() - fromTop);
        if (v.insns.size() != 1 || !(v.insns.iterator().next() instanceof MethodInsnNode g) || !g.name.equals("getBlock") || !g.desc.startsWith("()")) return false;
        Frame<SourceValue> gf = fr[m.instructions.indexOf(g)];
        return gf != null && isParam(gf, 1, fr, m, 0);
    }

    /** The value is this (ALOAD 0) or this.asState(). */
    private static boolean isThisState(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] fr, MethodNode m) {
        if (isParam(f, fromTop, fr, m, 0)) return true;
        if (f.getStackSize() < fromTop) return false;
        SourceValue v = f.getStack(f.getStackSize() - fromTop);
        if (v.insns.size() != 1 || !(v.insns.iterator().next() instanceof MethodInsnNode g) || !g.name.equals("asState")) return false;
        Frame<SourceValue> gf = fr[m.instructions.indexOf(g)];
        return gf != null && isParam(gf, 1, fr, m, 0);
    }

    /** Receiver is this.<field> (in an instance method). */
    private static boolean isOwnField(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] fr, MethodNode m, String field) {
        if (f.getStackSize() < fromTop) return false;
        SourceValue v = f.getStack(f.getStackSize() - fromTop);
        if (v.insns.size() != 1 || !(v.insns.iterator().next() instanceof org.objectweb.asm.tree.FieldInsnNode g) || g.getOpcode() != Opcodes.GETFIELD
                || !g.name.equals(field)) return false;
        Frame<SourceValue> gf = fr[m.instructions.indexOf(g)];
        return gf != null && isParam(gf, 1, fr, m, 0);
    }

    private static boolean isParam(Frame<SourceValue> f, int fromTop, Frame<SourceValue>[] fr, MethodNode m, int slot) {
        if (f.getStackSize() < fromTop) return false;
        SourceValue v = f.getStack(f.getStackSize() - fromTop);
        return v.insns.size() == 1 && v.insns.iterator().next() instanceof VarInsnNode vi && vi.getOpcode() == Opcodes.ALOAD && vi.var == slot;
    }

    private static boolean derives(SourceValue v, Frame<SourceValue>[] fr, MethodNode m, int posSlot, int depth) {
        if (depth > 8) return true;   // conservative
        for (AbstractInsnNode src : v.insns) {
            int op = src.getOpcode();
            if (src instanceof VarInsnNode vi && op == Opcodes.ALOAD) {
                if (vi.var == posSlot) return true;
                Frame<SourceValue> f = fr[m.instructions.indexOf(src)];
                if (f != null) {
                    SourceValue local = f.getLocal(vi.var);
                    for (AbstractInsnNode st : local.insns) {
                        if (st instanceof VarInsnNode s && s.getOpcode() == Opcodes.ASTORE) {
                            Frame<SourceValue> sf = fr[m.instructions.indexOf(st)];
                            if (sf != null && sf.getStackSize() > 0 && derives(sf.getStack(sf.getStackSize() - 1), fr, m, posSlot, depth + 1)) return true;
                        }
                    }
                }
            } else if (op == Opcodes.DUP || op == Opcodes.CHECKCAST || op == Opcodes.DUP_X1 || op == Opcodes.DUP_X2) {
                Frame<SourceValue> f = fr[m.instructions.indexOf(src)];
                if (f != null && f.getStackSize() > 0 && derives(f.getStack(f.getStackSize() - 1), fr, m, posSlot, depth + 1)) return true;
            }
        }
        return false;
    }

    static Resolved resolve(ClassLoader loader, String cls, String name, String desc) {
        List<String> ifaces = new ArrayList<>();
        for (String k = cls; k != null; ) {
            ClassNode cn = node(loader, k);
            if (cn == null) return null;
            for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc) && (m.access & Opcodes.ACC_ABSTRACT) == 0) return new Resolved(k, m);
            ifaces.addAll(cn.interfaces);
            k = cn.superName;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (!ifaces.isEmpty()) {   // default methods
            String i = ifaces.remove(ifaces.size() - 1);
            if (!seen.add(i)) continue;
            ClassNode cn = node(loader, i);
            if (cn == null) continue;
            for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc) && (m.access & Opcodes.ACC_ABSTRACT) == 0) return new Resolved(i, m);
            ifaces.addAll(cn.interfaces);
        }
        return null;
    }

    /** Top-level block method (state, level, pos, direction) with the given state context. */
    public static boolean nonRetaining(ClassLoader loader, String concrete, String name, String desc, int param, StateCtx ctx) {
        String key = concrete + "." + name + desc + "#" + param + ctx;
        return RESULTS.computeIfAbsent(key, k -> {
            try {
                Resolved r = resolve(loader, concrete, name, desc);
                return r != null && proven(loader, concrete, r, param, 0, ctx);
            } catch (Throwable t) {
                return false;
            }
        });
    }

    static ClassNode node(ClassLoader loader, String k) {
        ClassNode cached = NODES.get(k);
        if (cached != null) return cached;
        try (InputStream in = (loader != null ? loader : ClassLoader.getSystemClassLoader()).getResourceAsStream(k + ".class")) {
            if (in == null) return null;
            ClassNode cn = new ClassNode();
            new ClassReader(in).accept(cn, ClassReader.SKIP_FRAMES);
            NODES.put(k, cn);
            return cn;
        } catch (Exception e) {
            return null;
        }
    }
}
