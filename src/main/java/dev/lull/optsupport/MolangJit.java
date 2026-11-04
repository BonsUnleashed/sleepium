package dev.lull.optsupport;

import static org.objectweb.asm.Opcodes.*;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.Group;
import com.eliotlash.mclib.math.IValue;
import com.eliotlash.mclib.math.Negate;
import com.eliotlash.mclib.math.Negative;
import com.eliotlash.mclib.math.Operation;
import com.eliotlash.mclib.math.Operator;
import com.eliotlash.mclib.math.Ternary;
import com.eliotlash.mclib.math.Variable;
import com.eliotlash.mclib.math.functions.Function;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import software.bernie.geckolib.core.molang.expressions.MolangCompoundValue;
import software.bernie.geckolib.core.molang.expressions.MolangValue;
import software.bernie.geckolib.core.molang.expressions.MolangVariableHolder;

/**
 * Molang to JVM bytecode (geomixin/MolangJitMixin, {@code -Dlab.opt.MolangJitMixin=true}): an expression GeckoLib 4.8.4
 * parsed (MolangParser.parseExpression: mclib's IValue tree under GeckoLib's MolangValue statements) becomes one hidden
 * class whose get() is the tree as straight-line code, each node doing exactly what its own get() does:
 * <ul>
 *   <li>mclib's Operator evaluates both sides, then its Operation's calculate (no short circuit for && and ||;
 *       division by 0 divides by 1, modulo is Java's %, == / != within 1e-5, comparisons as javac compiles them),</li>
 *   <li>Ternary (condition != 0), Negate (value == 0 ? 1 : 0), Negative, Group,</li>
 *   <li>GeckoLib's statements: MolangValue (its value), MolangVariableHolder (value, then variable.set), and
 *       MolangCompoundValue (every statement in order, the last one's value - GeckoLib runs past a return too),</li>
 *   <li>variables through their own get() / set() (LazyVariable's supplier; MolangVariableMixin's job-local values
 *       for ParallelRender apply), constants through Constant.get() (mclib's Constant is mutable).</li>
 * </ul>
 * A function (mclib's, GeckoLib's or a mod's) stays its own object with its own get(): the arguments in its args array
 * are replaced by their compiled forms. Anything else (a mod's IValue, a subclass of a node class) is called as it is.
 * Bit-exact by construction; {@code -Dlab.molang.verify=true} compares every evaluation with GeckoLib's tree, and
 * also checks each side-effect-free expression once when it is compiled, over a set of variable values (selfCheck:
 * the variables' suppliers are swapped for the check and put back - verify mode only, the values are global).
 * <p>
 * 1.20.1: a rewrite for mclib's tree (1.21.1's GeckoLib 4.9 has its own MathValue tree: immutable subtrees folded,
 * short-circuit logic, assignments as values). The compiled expression is a MolangValue as the parsed one is:
 * BakedAnimationsAdapter reads isConstant() (false for both: their classes are not MolangValue itself),
 * getValueHolder() and isReturnValue() answer as the original's.
 */
public final class MolangJit {
    private MolangJit() {}

    public static final boolean VERIFY = Boolean.getBoolean("lab.molang.verify");
    public static long compiled, failed, verified, mismatches, selfChecked, selfMismatches;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** Base of the generated classes: the original tree (toString, verify), the constants, get() generated. */
    public abstract static class Compiled extends MolangValue {
        protected final IValue original;
        protected final Object[] k;

        protected Compiled(IValue original, Object[] k) {
            super(holderOf(original), original instanceof MolangValue m && m.isReturnValue());
            this.original = original;
            this.k = k;
        }

        private static IValue holderOf(IValue v) {
            return v instanceof MolangValue m ? m.getValueHolder() : v;
        }

        protected abstract double eval();

        @Override
        public final double get() {
            if (!VERIFY) return eval();
            double a = eval(), b = original.get();   // (side effects twice in verify mode: the same values set again)
            verified++;
            if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b) && mismatches++ < 10)
                System.out.println("[sleep/opt] molang jit mismatch: " + a + " vs " + b + " for " + original);
            return a;
        }

        @Override
        public String toString() {
            return original.toString();
        }

        /** the compiled code alone (tests) */
        public final double sleep$eval() {
            return eval();
        }

        /** GeckoLib's tree this was compiled from (tests) */
        public final IValue sleep$original() {
            return original;
        }
    }

    /** The compiled form of an expression, or the expression itself (a constant, or on any failure). */
    public static IValue compile(IValue expr) {
        if (expr == null || expr instanceof Compiled || expr instanceof Constant || expr instanceof Variable) return expr;
        try {
            IValue c = generate(expr);
            compiled++;
            if (VERIFY) selfCheck(expr, (Compiled) c);
            return c;
        } catch (Throwable t) {
            if (failed++ < 5) System.out.println("[sleep/opt] molang jit: kept as GeckoLib's tree (" + t + "): " + expr);
            return expr;
        }
    }

    /** MolangParser.parseExpression's result: a statement stays a MolangValue (the parser's callers read it as one) */
    public static MolangValue compile(MolangValue expr) {
        IValue c = compile((IValue) expr);
        return c instanceof MolangValue m ? m : expr;
    }

    // ------------------------------------------------------------------ code generation

    private static final String BASE = "dev/lull/optsupport/MolangJit$Compiled";
    private static final String IV = "com/eliotlash/mclib/math/IValue";
    private static final String VAR = "com/eliotlash/mclib/math/Variable";
    private static final String CONST = "com/eliotlash/mclib/math/Constant";

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static final class Gen {
        final MethodVisitor mv;
        final List<Object> consts = new ArrayList<>();
        final Map<Object, Integer> index = new IdentityHashMap<>();
        int nextLocal = 1;   // 0: this

        Gen(MethodVisitor mv) {
            this.mv = mv;
        }

        int doubleLocal() {
            int s = nextLocal;
            nextLocal += 2;
            return s;
        }

        /** push k[i] cast to the given internal name */
        void konst(Object o, String type) {
            Integer i = index.get(o);
            if (i == null) {
                index.put(o, i = consts.size());
                consts.add(o);
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, BASE, "k", "[Ljava/lang/Object;");
            mv.visitLdcInsn(i);
            mv.visitInsn(AALOAD);
            mv.visitTypeInsn(CHECKCAST, type);
        }

        /** the object's own get() (any IValue) */
        void call(IValue v) {
            konst(v, IV);
            mv.visitMethodInsn(INVOKEINTERFACE, IV, "get", "()D", true);
        }

        /** 1.0 if the jump is not taken, else 0.0 ({@code cond ? 1.0 : 0.0} with the jump meaning "false") */
        void boolFromJump(int jumpIfFalse) {
            Label f = new Label(), end = new Label();
            mv.visitJumpInsn(jumpIfFalse, f);
            mv.visitInsn(DCONST_1);
            mv.visitJumpInsn(GOTO, end);
            mv.visitLabel(f);
            mv.visitInsn(DCONST_0);
            mv.visitLabel(end);
        }

        /** leaves the node's value (double) on the stack */
        void emit(IValue v) throws ReflectiveOperationException {
            Class<?> c = v.getClass();
            if (v instanceof Variable var) {   // LazyVariable (GeckoLib's), its job-local values: its own get()
                konst(var, VAR);
                mv.visitMethodInsn(INVOKEVIRTUAL, VAR, "get", "()D", false);
            } else if (c == Constant.class) {
                konst(v, CONST);
                mv.visitMethodInsn(INVOKEVIRTUAL, CONST, "get", "()D", false);
            } else if (c == Operator.class) {
                operator((Operator) v);
            } else if (c == Ternary.class) {
                Ternary t = (Ternary) v;
                Label f = new Label(), end = new Label();
                emit(t.condition);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFEQ, f);   // condition != 0.0 ? ifTrue : ifFalse
                emit(t.ifTrue);
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(f);
                emit(t.ifFalse);
                mv.visitLabel(end);
            } else if (c == Negate.class) {
                emit(((Negate) v).value);
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                boolFromJump(IFNE);   // value == 0.0 ? 1.0 : 0.0
            } else if (c == Negative.class) {
                emit(((Negative) v).value);
                mv.visitInsn(DNEG);
            } else if (c == Group.class) {
                emit((IValue) GROUP_VALUE.get(v));
            } else if (c == MolangCompoundValue.class) {
                List<MolangValue> values = ((MolangCompoundValue) v).values;
                if (values.isEmpty()) {
                    mv.visitInsn(DCONST_0);
                    return;
                }
                for (int i = 0; i < values.size(); i++) {   // every statement, the last one's value
                    emit(values.get(i));
                    if (i < values.size() - 1) mv.visitInsn(POP2);
                }
            } else if (c == MolangVariableHolder.class && ((MolangVariableHolder) v).variable != null) {
                MolangVariableHolder h = (MolangVariableHolder) v;
                int t = doubleLocal();
                emit(h.getValueHolder());   // super.get()
                mv.visitVarInsn(DSTORE, t);
                konst(h.variable, VAR);
                mv.visitVarInsn(DLOAD, t);
                mv.visitMethodInsn(INVOKEVIRTUAL, VAR, "set", "(D)V", false);
                mv.visitVarInsn(DLOAD, t);
            } else if (c == MolangValue.class) {
                emit(((MolangValue) v).getValueHolder());
            } else if (v instanceof Function fn) {
                compileArgs(fn);
                call(fn);
            } else {
                call(v);   // a compiled part, a mod's value type or a subclass of a node: as it is
            }
        }

        private void operator(Operator o) throws ReflectiveOperationException {
            Operation op = o.operation;
            emit(o.a);   // both sides, always (Operator.get)
            emit(o.b);
            switch (op) {
                case ADD -> mv.visitInsn(DADD);
                case SUB -> mv.visitInsn(DSUB);
                case MUL -> mv.visitInsn(DMUL);
                case DIV -> {   // a / (b == 0.0 ? 1.0 : b)
                    int b = doubleLocal();
                    mv.visitVarInsn(DSTORE, b);
                    Label useB = new Label(), end = new Label();
                    mv.visitVarInsn(DLOAD, b);
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFNE, useB);
                    mv.visitInsn(DCONST_1);
                    mv.visitJumpInsn(GOTO, end);
                    mv.visitLabel(useB);
                    mv.visitVarInsn(DLOAD, b);
                    mv.visitLabel(end);
                    mv.visitInsn(DDIV);
                }
                case MOD -> mv.visitInsn(DREM);
                case POW -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "pow", "(DD)D", false);
                case AND -> {   // a != 0.0 && b != 0.0 ? 1.0 : 0.0 (both already evaluated)
                    int b = doubleLocal();
                    mv.visitVarInsn(DSTORE, b);
                    Label f = new Label(), end = new Label();
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFEQ, f);
                    mv.visitVarInsn(DLOAD, b);
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFEQ, f);
                    mv.visitInsn(DCONST_1);
                    mv.visitJumpInsn(GOTO, end);
                    mv.visitLabel(f);
                    mv.visitInsn(DCONST_0);
                    mv.visitLabel(end);
                }
                case OR -> {   // a == 0.0 && b == 0.0 ? 0.0 : 1.0
                    int b = doubleLocal();
                    mv.visitVarInsn(DSTORE, b);
                    Label t = new Label(), end = new Label();
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFNE, t);
                    mv.visitVarInsn(DLOAD, b);
                    mv.visitInsn(DCONST_0);
                    mv.visitInsn(DCMPL);
                    mv.visitJumpInsn(IFNE, t);
                    mv.visitInsn(DCONST_0);
                    mv.visitJumpInsn(GOTO, end);
                    mv.visitLabel(t);
                    mv.visitInsn(DCONST_1);
                    mv.visitLabel(end);
                }
                // as javac compiles them: < and <= with DCMPG, >= and > with DCMPL (NaN: false for all four)
                case LESS -> { mv.visitInsn(DCMPG); boolFromJump(IFGE); }
                case LESS_THAN -> { mv.visitInsn(DCMPG); boolFromJump(IFGT); }        // "<="
                case GREATER_THAN -> { mv.visitInsn(DCMPL); boolFromJump(IFLT); }     // ">="
                case GREATER -> { mv.visitInsn(DCMPL); boolFromJump(IFLE); }
                case EQUALS, NOT_EQUALS -> {   // Math.abs(a - b) < 1.0E-5, and its negation (NaN: not equal)
                    mv.visitInsn(DSUB);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
                    mv.visitLdcInsn(1.0E-5);
                    mv.visitInsn(DCMPG);
                    if (op == Operation.EQUALS) boolFromJump(IFGE);
                    else boolFromJump(IFLT);
                }
                default -> {   // an operation added later: its own calculate
                    int b = doubleLocal(), a = doubleLocal();
                    mv.visitVarInsn(DSTORE, b);
                    mv.visitVarInsn(DSTORE, a);
                    konst(op, "com/eliotlash/mclib/math/Operation");
                    mv.visitVarInsn(DLOAD, a);
                    mv.visitVarInsn(DLOAD, b);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "com/eliotlash/mclib/math/Operation", "calculate", "(DD)D", false);
                }
            }
        }
    }

    private static final Field GROUP_VALUE, FUNCTION_ARGS;

    static {
        try {
            GROUP_VALUE = field(Group.class, "value");
            FUNCTION_ARGS = field(Function.class, "args");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);   // (the mixin then fails to apply its handler: GeckoLib's trees stay)
        }
    }

    /** A function's arguments (its own args array) replaced by their compiled forms: its get() then reads compiled values. */
    private static void compileArgs(Function fn) throws ReflectiveOperationException {
        IValue[] args = (IValue[]) FUNCTION_ARGS.get(fn);
        if (args == null) return;
        for (int i = 0; i < args.length; i++) args[i] = compile(args[i]);
    }

    private static IValue generate(IValue expr) throws ReflectiveOperationException {
        String name = "dev/lull/optsupport/Molang$" + IDS.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";   // frames only hold `this` and doubles
            }
        };
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL, name, null, BASE, null);
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "(L" + IV + ";[Ljava/lang/Object;)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ALOAD, 1);
        init.visitVarInsn(ALOAD, 2);
        init.visitMethodInsn(INVOKESPECIAL, BASE, "<init>", "(L" + IV + ";[Ljava/lang/Object;)V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor mv = cw.visitMethod(ACC_PROTECTED, "eval", "()D", null, null);
        mv.visitCode();
        Gen g = new Gen(mv);
        g.emit(expr);
        mv.visitInsn(DRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        MethodHandles.Lookup hidden = LOOKUP.defineHiddenClass(cw.toByteArray(), true);
        return (IValue) hidden.lookupClass().getConstructor(IValue.class, Object[].class).newInstance(expr, g.consts.toArray());
    }

    // ------------------------------------------------------------------ verify mode: a check at compile time

    private static final double[] PROBES = {0, 1, -1, 0.5, 2.75, -13.25, 1e-6, 359.5, 1e9, -0.0, Double.NaN, Double.POSITIVE_INFINITY, 1e-5, 0.3};
    private static final Field LAZY_SUPPLIER;

    static {
        Field f = null;
        try {
            f = field(software.bernie.geckolib.core.molang.LazyVariable.class, "valueSupplier");
        } catch (NoSuchFieldException e) {
            // no check then
        }
        LAZY_SUPPLIER = f;
    }

    /**
     * Compiled code against GeckoLib's tree for an expression without side effects (no assignments, no random
     * functions, no unknown value types), every variable it reads given each probe value in turn (shifted per
     * variable), the variables' own suppliers put back afterwards. Animations a scene never plays get checked too.
     */
    private static void selfCheck(IValue expr, Compiled c) {
        java.util.List<Variable> vars = new ArrayList<>();
        try {
            if (LAZY_SUPPLIER == null || !pure(expr, vars)) return;
            Object[] saved = new Object[vars.size()];
            for (int i = 0; i < vars.size(); i++) saved[i] = LAZY_SUPPLIER.get(vars.get(i));
            try {
                for (int p = 0; p < PROBES.length; p++) {
                    for (int i = 0; i < vars.size(); i++) {
                        double v = PROBES[(p + i) % PROBES.length];
                        LAZY_SUPPLIER.set(vars.get(i), (java.util.function.DoubleSupplier) () -> v);
                    }
                    double a = c.sleep$eval(), b = expr.get();
                    if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b) && selfMismatches++ < 10)
                        System.out.println("[sleep/opt] molang jit self-check mismatch: " + a + " vs " + b + " for " + expr);
                }
                selfChecked++;
            } finally {
                for (int i = 0; i < vars.size(); i++) LAZY_SUPPLIER.set(vars.get(i), saved[i]);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[sleep/opt] molang jit self-check skipped (" + e + "): " + expr);
        }
    }

    private static final java.util.Set<String> IMPURE = java.util.Set.of("Random", "RandomInteger", "DieRoll", "DieRollInteger");

    /** side-effect free and made only of the known node types; collects the (lazy) variables it reads */
    private static boolean pure(IValue v, java.util.List<Variable> vars) throws ReflectiveOperationException {
        Class<?> c = v.getClass();
        if (v instanceof Compiled k) return pure(k.original, vars);
        if (c == software.bernie.geckolib.core.molang.LazyVariable.class) {
            if (!vars.contains(v)) vars.add((Variable) v);
            return true;
        }
        if (c == Constant.class) return true;
        if (c == Operator.class) return pure(((Operator) v).a, vars) && pure(((Operator) v).b, vars);
        if (c == Ternary.class) return pure(((Ternary) v).condition, vars) && pure(((Ternary) v).ifTrue, vars) && pure(((Ternary) v).ifFalse, vars);
        if (c == Negate.class) return pure(((Negate) v).value, vars);
        if (c == Negative.class) return pure(((Negative) v).value, vars);
        if (c == Group.class) return pure((IValue) GROUP_VALUE.get(v), vars);
        if (c == MolangValue.class) return pure(((MolangValue) v).getValueHolder(), vars);
        if (c == MolangCompoundValue.class) {
            for (MolangValue s : ((MolangCompoundValue) v).values) if (!pure(s, vars)) return false;
            return true;
        }
        if (v instanceof Function fn && (c.getName().startsWith("com.eliotlash.mclib.math.functions.") && !IMPURE.contains(c.getSimpleName())
                || c.getName().startsWith("software.bernie.geckolib.core.molang.functions."))) {
            for (IValue a : (IValue[]) FUNCTION_ARGS.get(fn)) if (!pure(a, vars)) return false;
            return true;
        }
        return false;   // assignments, random functions, a mod's types
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "molang jit: compiled %d, kept as trees %d%s", compiled, failed,
                VERIFY ? ", verified " + verified + ", mismatches " + mismatches + "; self-checked at compile time " + selfChecked
                        + " (x " + PROBES.length + " variable values), mismatches " + selfMismatches : "");
    }
}
