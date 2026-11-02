package dev.lull.optsupport;

import static org.objectweb.asm.Opcodes.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.math.Operator;
import software.bernie.geckolib.loading.math.function.MathFunction;
import software.bernie.geckolib.loading.math.value.BooleanNegate;
import software.bernie.geckolib.loading.math.value.Calculation;
import software.bernie.geckolib.loading.math.value.CompoundValue;
import software.bernie.geckolib.loading.math.value.Constant;
import software.bernie.geckolib.loading.math.value.Group;
import software.bernie.geckolib.loading.math.value.Negative;
import software.bernie.geckolib.loading.math.value.Ternary;
import software.bernie.geckolib.loading.math.value.Variable;
import software.bernie.geckolib.loading.math.value.VariableAssignment;

/**
 * Molang to JVM bytecode (probe-geo MolangJitMixin, {@code -Dlab.opt.MolangJitMixin=true}): a mutable expression GeckoLib
 * parsed (its MathValue tree) becomes one hidden class whose get() is the tree as straight-line code, each node doing
 * what its own get() does: the operators' arithmetic as GeckoLib's Operator lambdas compute it (division / modulo by 0
 * gives a, == within 1e-5, || and && short-circuiting as Calculation does), ternaries, negations, groups, statement
 * sequences, assignments (Variable.set) and variables (Variable.get: GeoParallel's job-local values apply). An immutable
 * subtree is GeckoLib's own cached value (its get() once, as GeckoLib caches it). A function (GeckoLib's or a mod's)
 * stays its own object with its own compute(): its argument fields get compiled values. Anything else (a mod's
 * operator or value type) is called as it is. Bit-exact by construction; {@code -Dlab.molang.verify=true} compares
 * every evaluation with GeckoLib's tree.
 */
public final class MolangJit {
    private MolangJit() {}

    public static final boolean VERIFY = Boolean.getBoolean("lab.molang.verify");
    public static long compiled, failed, verified, mismatches;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /** Base of the generated classes: the original tree (isMutable, toString, verify), the constants, get() generated. */
    public abstract static class Compiled implements MathValue {
        protected final MathValue original;
        protected final Object[] k;
        private final boolean mutable;

        protected Compiled(MathValue original, Object[] k) {
            this.original = original;
            this.k = k;
            this.mutable = original.isMutable();
        }

        protected abstract double eval();

        @Override
        public final double get() {
            if (!VERIFY) return eval();
            double a = eval(), b = original.get();   // (side effects twice in verify mode)
            verified++;
            if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b)) mismatches++;
            return a;
        }

        @Override
        public final boolean isMutable() {
            return mutable;
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
        public final MathValue sleep$original() {
            return original;
        }
    }

    /** The compiled form of a mutable expression, or the expression itself (immutable, or on any failure). */
    public static MathValue compile(MathValue expr) {
        if (expr == null || !expr.isMutable() || expr instanceof Compiled || expr instanceof Constant) return expr;
        try {
            MathValue c = generate(expr);
            compiled++;
            return c;
        } catch (Throwable t) {
            if (failed++ < 5) System.out.println("[sleep/opt] molang jit: kept as GeckoLib's tree (" + t + "): " + expr);
            return expr;
        }
    }

    // ------------------------------------------------------------------ code generation

    private static final String BASE = "dev/lull/optsupport/MolangJit$Compiled";
    private static final String MV = "software/bernie/geckolib/loading/math/MathValue";

    private static final class Gen {
        final MethodVisitor mv;
        final List<Object> consts = new ArrayList<>();
        final Map<Object, Integer> index = new IdentityHashMap<>();

        Gen(MethodVisitor mv) {
            this.mv = mv;
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

        /** 1.0 if the condition jump is not taken, else 0.0: {@code cond ? 1 : 0} with the jump meaning "false" */
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
        void emit(MathValue v) throws ReflectiveOperationException {
            if (v instanceof Constant c) {
                mv.visitLdcInsn(c.value());
            } else if (!v.isMutable()) {
                mv.visitLdcInsn(v.get());   // GeckoLib's own cached value for an immutable subtree
            } else if (v instanceof Variable var) {
                konst(var, "software/bernie/geckolib/loading/math/value/Variable");
                mv.visitMethodInsn(INVOKEVIRTUAL, "software/bernie/geckolib/loading/math/value/Variable", "get", "()D", false);
            } else if (v instanceof Calculation calc) {
                calculation(calc);
            } else if (v instanceof Ternary t) {
                Label f = new Label(), end = new Label();
                emit(t.condition());
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(IFEQ, f);   // condition == 0 -> false branch
                emit(t.trueValue());
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(f);
                emit(t.falseValue());
                mv.visitLabel(end);
            } else if (v instanceof Negative n) {
                emit(n.value());
                mv.visitInsn(DNEG);
            } else if (v instanceof BooleanNegate b) {
                emit(b.value());
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                boolFromJump(IFNE);   // value == 0 ? 1 : 0
            } else if (v instanceof Group g) {
                emit(g.contents());
            } else if (v instanceof CompoundValue cv) {
                MathValue[] subs = cv.subValues();
                for (int i = 0; i < subs.length - 1; i++) {
                    emit(subs[i]);
                    mv.visitInsn(POP2);
                }
                emit(subs[subs.length - 1]);
            } else if (v instanceof VariableAssignment va) {
                konst(va.variable(), "software/bernie/geckolib/loading/math/value/Variable");
                emit(va.value());
                mv.visitMethodInsn(INVOKEVIRTUAL, "software/bernie/geckolib/loading/math/value/Variable", "set", "(D)V", false);
                mv.visitInsn(DCONST_0);
            } else if (v instanceof MathFunction fn) {
                compileArgs(fn);
                konst(fn, MV);
                mv.visitMethodInsn(INVOKEINTERFACE, MV, "get", "()D", true);
            } else {
                konst(v, MV);   // a mod's value type: as it is
                mv.visitMethodInsn(INVOKEINTERFACE, MV, "get", "()D", true);
            }
        }

        private void calculation(Calculation calc) throws ReflectiveOperationException {
            Operator op = calc.operator();
            if (op == Operator.OR || op == Operator.AND) {   // Calculation.compute: a first, b only if needed
                Label shortCut = new Label(), end = new Label();
                emit(calc.argA());
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                mv.visitJumpInsn(op == Operator.OR ? IFNE : IFEQ, shortCut);
                emit(calc.argB());
                mv.visitInsn(DCONST_0);
                mv.visitInsn(DCMPL);
                boolFromJump(IFEQ);   // b != 0 ? 1 : 0
                mv.visitJumpInsn(GOTO, end);
                mv.visitLabel(shortCut);
                mv.visitInsn(op == Operator.OR ? DCONST_1 : DCONST_0);
                mv.visitLabel(end);
                return;
            }
            emit(calc.argA());
            emit(calc.argB());
            if (op == Operator.ADD) mv.visitInsn(DADD);
            else if (op == Operator.SUB) mv.visitInsn(DSUB);
            else if (op == Operator.MUL) mv.visitInsn(DMUL);
            else if (op == Operator.DIV || op == Operator.MOD) divMod(op == Operator.DIV ? DDIV : DREM);
            else if (op == Operator.POW) mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "pow", "(DD)D", false);
            else if (op == Operator.EQUAL || op == Operator.NOT_EQUAL) {   // Math.abs(a - b) < 0.00001 (==) / >= (!=)
                mv.visitInsn(DSUB);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
                mv.visitLdcInsn(0.00001);
                // as javac compiles them: < with DCMPG / IFGE, >= with DCMPL / IFLT (NaN: false for both)
                mv.visitInsn(op == Operator.EQUAL ? DCMPG : DCMPL);
                boolFromJump(op == Operator.EQUAL ? IFGE : IFLT);
            } else if (op == Operator.LT) { mv.visitInsn(DCMPG); boolFromJump(IFGE); }
            else if (op == Operator.LTE) { mv.visitInsn(DCMPG); boolFromJump(IFGT); }
            else if (op == Operator.GT) { mv.visitInsn(DCMPL); boolFromJump(IFLE); }
            else if (op == Operator.GTE) { mv.visitInsn(DCMPL); boolFromJump(IFLT); }
            else if (op == Operator.ASSIGN_VARIABLE) {   // (a, b) -> 0, both evaluated
                mv.visitInsn(POP2);
                mv.visitInsn(POP2);
                mv.visitInsn(DCONST_0);
            } else {   // a mod's operator: its own compute
                int b = 3, a = 1;   // locals: a (1-2), b (3-4)
                mv.visitVarInsn(DSTORE, b);
                mv.visitVarInsn(DSTORE, a);
                konst(op, "software/bernie/geckolib/loading/math/Operator");
                mv.visitVarInsn(DLOAD, a);
                mv.visitVarInsn(DLOAD, b);
                mv.visitMethodInsn(INVOKEVIRTUAL, "software/bernie/geckolib/loading/math/Operator", "compute", "(DD)D", false);
            }
        }

        /** b == 0 ? a : a op b (GeckoLib's DIV / MOD); stack a, b */
        private void divMod(int insn) {
            mv.visitVarInsn(DSTORE, 3);
            mv.visitVarInsn(DSTORE, 1);
            Label zero = new Label(), end = new Label();
            mv.visitVarInsn(DLOAD, 3);
            mv.visitInsn(DCONST_0);
            mv.visitInsn(DCMPL);
            mv.visitJumpInsn(IFEQ, zero);
            mv.visitVarInsn(DLOAD, 1);
            mv.visitVarInsn(DLOAD, 3);
            mv.visitInsn(insn);
            mv.visitJumpInsn(GOTO, end);
            mv.visitLabel(zero);
            mv.visitVarInsn(DLOAD, 1);
            mv.visitLabel(end);
        }
    }

    /**
     * A function's MathValue argument fields (the objects its getArgs() lists) replaced by their compiled forms: its own
     * compute() then reads compiled values. Only fields holding one of its arguments are touched.
     */
    private static void compileArgs(MathFunction fn) throws ReflectiveOperationException {
        MathValue[] args = fn.getArgs();
        for (Class<?> c = fn.getClass(); c != null && c != MathFunction.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != MathValue.class) continue;
                f.setAccessible(true);
                Object cur = f.get(fn);
                boolean isArg = false;
                for (MathValue a : args) if (a == cur) isArg = true;
                if (!isArg) continue;
                MathValue arg = (MathValue) cur;
                if (arg instanceof Constant || arg instanceof Variable || arg instanceof Compiled || !arg.isMutable()) continue;
                f.set(fn, compile(arg));
            }
        }
    }

    private static MathValue generate(MathValue expr) throws ReflectiveOperationException {
        String name = "dev/lull/optsupport/Molang$" + IDS.incrementAndGet();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        cw.visit(V21, ACC_PUBLIC | ACC_FINAL, name, null, BASE, null);
        MethodVisitor init = cw.visitMethod(ACC_PUBLIC, "<init>", "(L" + MV + ";[Ljava/lang/Object;)V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitVarInsn(ALOAD, 1);
        init.visitVarInsn(ALOAD, 2);
        init.visitMethodInsn(INVOKESPECIAL, BASE, "<init>", "(L" + MV + ";[Ljava/lang/Object;)V", false);
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
        return (MathValue) hidden.lookupClass().getConstructor(MathValue.class, Object[].class).newInstance(expr, g.consts.toArray());
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "molang jit: compiled %d, kept as trees %d%s", compiled, failed,
                VERIFY ? ", verified " + verified + ", mismatches " + mismatches : "");
    }
}
