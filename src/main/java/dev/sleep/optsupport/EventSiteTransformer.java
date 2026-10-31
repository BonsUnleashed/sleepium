package dev.sleep.optsupport;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
 * Rewrites {@code NeoForge.EVENT_BUS.post(event)} into an invokedynamic bound to {@link EventSites} wherever the
 * receiver is that static field and the event's exact class is known from its {@code new E(...)} (directly or through
 * a local). Every other post is left as it is. Called from OptMixinPlugin.postApply for EventSiteMixin's targets.
 */
public final class EventSiteTransformer {
    private static final String BUS = "net/neoforged/bus/api/IEventBus";
    private static final String POST_DESC = "(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;";
    private static final Handle BSM = new Handle(Opcodes.H_INVOKESTATIC, "dev/sleep/optsupport/EventSites", "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false);
    public static int rewritten, skipped;

    private EventSiteTransformer() {}

    public static void transform(ClassNode cn) {
        int before = rewritten;
        for (MethodNode m : cn.methods) {
            boolean has = false;
            for (AbstractInsnNode in : m.instructions) if (isPost(in)) { has = true; break; }
            if (!has) continue;
            Frame<SourceValue>[] fr;
            try {
                fr = new Analyzer<>(new SourceInterpreter()).analyze(cn.name, m);
            } catch (Exception e) {
                continue;
            }
            AbstractInsnNode[] insns = m.instructions.toArray();
            for (int i = 0; i < insns.length; i++) {
                if (!isPost(insns[i]) || fr[i] == null) continue;
                Frame<SourceValue> f = fr[i];
                String type = eventType(f.getStack(f.getStackSize() - 1), fr, m, 0);
                boolean busField = isEventBusField(f.getStack(f.getStackSize() - 2));
                if (type == null || !busField) {
                    skipped++;
                    continue;
                }
                m.instructions.set(insns[i], new InvokeDynamicInsnNode("post", "(L" + BUS + ";Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
                        BSM, Type.getObjectType(type)));
                rewritten++;
            }
        }
        System.out.println("[sleep/opt] event sites: " + cn.name + " " + (rewritten - before) + " post calls rewritten");
    }

    private static boolean isPost(AbstractInsnNode in) {
        return in instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEINTERFACE && mi.owner.equals(BUS) && mi.name.equals("post")
                && mi.desc.equals(POST_DESC);
    }

    private static boolean isEventBusField(SourceValue v) {
        if (v.insns.size() != 1) return false;
        return v.insns.iterator().next() instanceof FieldInsnNode fi && fi.getOpcode() == Opcodes.GETSTATIC
                && fi.owner.equals("net/neoforged/neoforge/common/NeoForge") && fi.name.equals("EVENT_BUS");
    }

    /** The class of NEW that produced this value (through DUP, CHECKCAST or a single-store local), else null. */
    private static String eventType(SourceValue v, Frame<SourceValue>[] fr, MethodNode m, int depth) {
        if (v.insns.size() != 1 || depth > 6) return null;
        AbstractInsnNode src = v.insns.iterator().next();
        int idx = m.instructions.indexOf(src);
        Frame<SourceValue> f = fr[idx];
        if (src instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW) return t.desc;
        if (f == null) return null;
        if (src.getOpcode() == Opcodes.DUP || src.getOpcode() == Opcodes.CHECKCAST) return eventType(f.getStack(f.getStackSize() - 1), fr, m, depth + 1);
        if (src instanceof VarInsnNode vi && vi.getOpcode() == Opcodes.ALOAD) {
            SourceValue local = f.getLocal(vi.var);
            if (local.insns.size() != 1) return null;
            AbstractInsnNode st = local.insns.iterator().next();
            if (!(st instanceof VarInsnNode s) || s.getOpcode() != Opcodes.ASTORE) return null;
            Frame<SourceValue> sf = fr[m.instructions.indexOf(st)];
            return sf == null ? null : eventType(sf.getStack(sf.getStackSize() - 1), fr, m, depth + 1);
        }
        return null;
    }
}
