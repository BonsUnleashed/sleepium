package dev.lull.optsupport;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Methods too large for the JIT, cut into parts it compiles. HotSpot never compiles a method of more than 8 000 bytes
 * of bytecode (DontCompileHugeMethods, a startup flag a mod cannot change): a code generator's long straight-line
 * method runs in the interpreter for good. Entity Model Features before 3.3 compiles each model's animation into one
 * such method (Fresh Animations: 35 % of a frame interpreted on 1.20.1).
 *
 * <p>A static void method is cut where nothing crosses: an empty operand stack, no jump or switch across, no local
 * variable live across (liveness over the control flow), its parameters never stored to, no return before the end.
 * Each run of instructions becomes a static method of the same descriptor, called in order with the same arguments.
 * The same instructions in the same order on the same values: the same results, bit for bit. A method that has no
 * such cut points, or anything unusual (handlers, a return in the middle), is left as it is.
 */
public final class HugeMethods {
    /** cut methods with more instructions than this (about EMF 3.3's own chunk, ~2-3 bytes each: under 8 000 bytes) */
    private static final int HUGE_INSNS = Integer.getInteger("lab.hugeMethods.huge", 2500);
    /** instructions per part, at least (a part ends at the next cut point after this many) */
    private static final int PART_INSNS = Integer.getInteger("lab.hugeMethods.part", 1200);

    public static final AtomicInteger methodsCut = new AtomicInteger(), partsMade = new AtomicInteger(), refused = new AtomicInteger();
    private static final AtomicInteger logged = new AtomicInteger();

    private HugeMethods() {}

    /** A generated class' bytes with its huge static methods cut into parts; the bytes as they were on any doubt. */
    public static byte[] split(byte[] bytes) {
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);
            List<MethodNode> added = new ArrayList<>();
            for (MethodNode m : cn.methods) {
                if (m.instructions.size() <= HUGE_INSNS) continue;
                List<MethodNode> ps = cut(cn.name, m);
                if (ps == null) {
                    refused.incrementAndGet();
                    continue;
                }
                added.addAll(ps);
                methodsCut.incrementAndGet();
                partsMade.addAndGet(ps.size());
            }
            if (added.isEmpty()) return bytes;
            cn.methods.addAll(added);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String a, String b) {
                    return "java/lang/Object";   // generated straight-line code: no frame merges of other reference types
                }
            };
            cn.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            if (logged.getAndIncrement() < 3) System.out.println("[sleep/opt] huge methods: a class left as it was: " + t);
            return bytes;
        }
    }

    /** the parts of m (m's body then calls them), or null: m stays as it is */
    static List<MethodNode> cut(String owner, MethodNode m) throws Exception {
        if ((m.access & Opcodes.ACC_STATIC) == 0 || !Type.getReturnType(m.desc).equals(Type.VOID_TYPE)) return null;
        if (m.tryCatchBlocks != null && !m.tryCatchBlocks.isEmpty()) return null;
        Type[] args = Type.getArgumentTypes(m.desc);
        int paramSlots = 0;
        for (Type t : args) paramSlots += t.getSize();
        AbstractInsnNode[] insns = m.instructions.toArray();
        int n = insns.length;
        Map<AbstractInsnNode, Integer> index = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) index.put(insns[i], i);

        // returns only at the end (the last real instruction), parameters never stored to
        int lastReal = n - 1;
        while (lastReal >= 0 && insns[lastReal].getOpcode() < 0) lastReal--;
        for (int i = 0; i < n; i++) {
            AbstractInsnNode in = insns[i];
            int op = in.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN && i != lastReal) return null;
            if (in instanceof VarInsnNode v && isStore(op) && v.var < paramSlots) return null;
            if (in instanceof IincInsnNode inc && inc.var < paramSlots) return null;
            if (op == Opcodes.JSR || op == Opcodes.RET) return null;
        }

        // the operand stack at each instruction, and the control flow edges
        List<int[]> edges = new ArrayList<>();
        Analyzer<BasicValue> an = new Analyzer<>(new BasicInterpreter()) {
            @Override
            protected void newControlFlowEdge(int insn, int successor) {
                edges.add(new int[] {insn, successor});
            }
        };
        Frame<BasicValue>[] frames = an.analyze(owner, m);

        // no jump or switch across a cut: coverage of [min, max] of every branch
        int[] cover = new int[n + 1];
        for (int i = 0; i < n; i++) {
            List<LabelNode> targets = new ArrayList<>();
            if (insns[i] instanceof JumpInsnNode j) targets.add(j.label);
            else if (insns[i] instanceof TableSwitchInsnNode t) {
                targets.add(t.dflt);
                targets.addAll(t.labels);
            } else if (insns[i] instanceof LookupSwitchInsnNode l) {
                targets.add(l.dflt);
                targets.addAll(l.labels);
            }
            for (LabelNode t : targets) {
                int b = index.get(t);
                int lo = Math.min(i, b), hi = Math.max(i, b);
                cover[lo + 1]++;   // a cut before instruction k is crossed when lo < k <= hi
                cover[hi + 1]--;
            }
        }
        for (int i = 1; i <= n; i++) cover[i] += cover[i - 1];

        // liveness of the locals beyond the parameters: live-in = use + (live-out - def), over the edges, to a fixpoint
        List<List<Integer>> succ = new ArrayList<>(n);
        for (int i = 0; i < n; i++) succ.add(new ArrayList<>(2));
        for (int[] e : edges) succ.get(e[0]).add(e[1]);
        BitSet[] liveIn = new BitSet[n];
        for (int i = 0; i < n; i++) liveIn[i] = new BitSet();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                BitSet out = new BitSet();
                for (int s : succ.get(i)) out.or(liveIn[s]);
                AbstractInsnNode in = insns[i];
                if (in instanceof VarInsnNode v) {
                    if (isStore(v.getOpcode())) {
                        out.clear(v.var);
                        if (size(v.getOpcode()) == 2) out.clear(v.var + 1);
                    } else out.set(v.var);
                } else if (in instanceof IincInsnNode inc) out.set(inc.var);
                if (!out.equals(liveIn[i])) {
                    liveIn[i] = out;
                    changed = true;
                }
            }
        }

        // cut points: before instruction k, after enough instructions since the last one
        List<Integer> cuts = new ArrayList<>();
        int since = 0;
        for (int k = 0; k < n; k++) {
            if (since >= PART_INSNS && k <= lastReal && frames[k] != null && frames[k].getStackSize() == 0 && cover[k] == 0
                    && liveIn[k].nextSetBit(paramSlots) < 0) {
                cuts.add(k);
                since = 0;
            }
            if (insns[k].getOpcode() >= 0) since++;
        }
        if (cuts.isEmpty()) return null;

        // the parts, then m's body: each part called in order with m's arguments
        List<MethodNode> parts = new ArrayList<>();
        int from = 0;
        for (int p = 0; p <= cuts.size(); p++) {
            int to = p < cuts.size() ? cuts.get(p) : n;
            MethodNode part = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, m.name + "$sleepPart" + p, m.desc, null, null);
            Map<LabelNode, LabelNode> labels = new HashMap<>();
            for (AbstractInsnNode in : insns) if (in instanceof LabelNode l) labels.put(l, new LabelNode());
            for (int i = from; i < to; i++) {
                if (insns[i] instanceof FrameNode) continue;
                part.instructions.add(insns[i].clone(labels));
            }
            if (to < n) part.instructions.add(new InsnNode(Opcodes.RETURN));
            parts.add(part);
            from = to;
        }
        InsnList body = new InsnList();
        for (MethodNode part : parts) {
            int slot = 0;
            for (Type t : args) {
                body.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
                slot += t.getSize();
            }
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, part.name, part.desc, false));
        }
        body.add(new InsnNode(Opcodes.RETURN));
        m.instructions = body;
        m.localVariables = null;
        m.maxLocals = paramSlots;
        return parts;
    }

    private static boolean isStore(int op) {
        return op >= Opcodes.ISTORE && op <= Opcodes.ASTORE;
    }

    private static int size(int op) {
        return op == Opcodes.LSTORE || op == Opcodes.DSTORE || op == Opcodes.LLOAD || op == Opcodes.DLOAD ? 2 : 1;
    }

    public static String report() {
        return "huge methods cut " + methodsCut.get() + " into " + partsMade.get() + " parts" + (refused.get() > 0 ? ", left as they were " + refused.get() : "");
    }
}
