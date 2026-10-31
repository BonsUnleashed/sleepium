package dev.sleep.optsupport;

import java.util.Arrays;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * WorldgenAlloc (Ap2): the temporary double[] that DensityFunctions.Ap2.fillArray (ADD) allocates per call, taken from
 * a per-thread stack by nesting depth instead (ADD nodes nest: the argument's own fillArray takes the next depth).
 * Zero-filled like a new array. give() is called when the ADD loop is done with it.
 */
public final class ScratchArrays {
    private static final int MAX_DEPTH = 64;

    private static final class Stack {
        final double[][] arrays = new double[MAX_DEPTH][];
        int depth;
    }

    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);

    private ScratchArrays() {}

    public static double[] take(int length) {
        Stack s = STACK.get();
        int d = s.depth++;
        if (d >= MAX_DEPTH) return new double[length];
        double[] a = s.arrays[d];
        if (a == null || a.length != length) s.arrays[d] = a = new double[length];
        else Arrays.fill(a, 0.0);
        return a;
    }

    public static void give(double[] a) {
        STACK.get().depth--;
    }

    /**
     * Rewrites Ap2.fillArray: {@code double[] t = new double[a.length]; argument2.fillArray(t, p); for (...) a[k] += t[k];}
     * becomes take(a.length) … give(t) at the loop's exit. Only if the bytecode has exactly that shape (one NEWARRAY
     * double stored to a local, then the loop's IF_ICMPGE exit); otherwise the class is left alone.
     */
    public static void transform(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("fillArray") || !m.desc.equals("([DLnet/minecraft/world/level/levelgen/DensityFunction$ContextProvider;)V")) continue;
            AbstractInsnNode na = null;
            int count = 0;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext())
                if (i.getOpcode() == Opcodes.NEWARRAY && ((IntInsnNode) i).operand == Opcodes.T_DOUBLE) {
                    na = i;
                    count++;
                }
            if (count != 1) return;
            AbstractInsnNode st = next(na);
            if (st == null || st.getOpcode() != Opcodes.ASTORE) return;
            int local = ((VarInsnNode) st).var;
            AbstractInsnNode exit = null;
            for (AbstractInsnNode i = st.getNext(); i != null; i = i.getNext())
                if (i.getOpcode() == Opcodes.IF_ICMPGE) {
                    exit = i;
                    break;
                }
            if (exit == null) return;
            LabelNode target = ((JumpInsnNode) exit).label;
            // the loop's exit label must be reached only by that jump (the ADD branch): count jumps to it
            int refs = 0;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext())
                if (i instanceof JumpInsnNode j && j.label == target) refs++;
            if (refs != 1) return;
            AbstractInsnNode at = target;
            while (at.getNext() instanceof FrameNode || at.getNext() instanceof LineNumberNode) at = at.getNext();
            m.instructions.set(na, new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/sleep/optsupport/ScratchArrays", "take", "(I)[D", false));
            InsnList rel = new InsnList();
            rel.add(new VarInsnNode(Opcodes.ALOAD, local));
            rel.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/sleep/optsupport/ScratchArrays", "give", "([D)V", false));
            m.instructions.insert(at, rel);
            System.out.println("[probe/opt] Ap2.fillArray: temporary arrays from the per-thread stack");
            return;
        }
    }

    private static AbstractInsnNode next(AbstractInsnNode i) {
        AbstractInsnNode n = i.getNext();
        while (n != null && (n instanceof LabelNode || n instanceof LineNumberNode || n instanceof FrameNode)) n = n.getNext();
        return n;
    }
}
