package dev.lull.optsupport;

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
 * a local). Every other post is left as it is. Called at the end of the class' transformation for EventSiteMixin's
 * targets (LateEventSites), or from OptMixinPlugin.postApply when Mixin's extensions are out of reach.
 */
public final class EventSiteTransformer {
    private static final String BUS = "net/neoforged/bus/api/IEventBus";
    private static final String POST_DESC = "(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;";
    private static final Handle BSM = new Handle(Opcodes.H_INVOKESTATIC, "dev/lull/optsupport/EventSites", "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;", false);
    public static int rewritten, skipped, leftAlone;

    private EventSiteTransformer() {}

    /**
     * {@code late}: the class as every mod's mixins left it (LateEventSites), {@code before} its posts per method before
     * them (null: not counted). Otherwise (postApply, before other mods' late injectors) a method another mod's mixins
     * touch is left alone.
     */
    public static void transform(ClassNode cn, boolean late, java.util.Map<String, Integer> before) {
        int was = rewritten, leftBefore = leftAlone;
        java.util.Map<String, java.util.Set<String>> named = new java.util.TreeMap<>();   // method -> other mods' mixin configs in it
        java.util.Map<String, Integer> now = postsPerMethod(cn);
        if (before != null)   // posts gone since: another mod wrapped or redirected them - theirs, not rewritten
            for (java.util.Map.Entry<String, Integer> e : before.entrySet())
                if (e.getValue() > (now == null ? 0 : now.getOrDefault(e.getKey(), 0))) {
                    String name = e.getKey().substring(0, e.getKey().indexOf('('));
                    named.put(name, MixinTouch.touchers(cn.name, name));
                }
        for (MethodNode m : cn.methods) {
            boolean has = false;
            for (AbstractInsnNode in : m.instructions) if (isPost(in)) { has = true; break; }
            if (!has) continue;
            // postApply: another mod's mixin in this method - left alone. A MixinExtras wrap of the same post keeps the
            // call instruction this would replace and later builds its wrapper around it - the two together were invalid
            // bytecode (VerifyError: operand stack underflow in CommonHooks.onEntityIncomingDamage, a player's server)
            if (!late && MixinTouch.touched(cn.name, m.name)) {
                for (AbstractInsnNode in : m.instructions) if (isPost(in)) leftAlone++;
                named.put(m.name, MixinTouch.touchers(cn.name, m.name));
                continue;
            }
            Frame<SourceValue>[] fr;
            try {
                fr = dev.lull.ClassBytes.analyze(new Analyzer<>(new SourceInterpreter()), cn.name, m);   // (other mods' code: sizes from before it)
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
        if (dev.lull.ClassBytes.REREAD.get() != null) {   // (readRunning's copy for the analyses: counted and logged once, as loaded)
            rewritten = was;
            leftAlone = leftBefore;
            return;
        }
        System.out.println("[sleep/opt] event sites: " + cn.name + " " + (rewritten - was) + " post calls rewritten"
                + (late ? " after every other transformation" : "")
                + (leftAlone > leftBefore ? ", " + (leftAlone - leftBefore) + " left alone (other mods' mixins in their methods)" : "")
                + named(late ? "posts other mods wrap, left to them: " : "", named));
    }

    /** posts per method ("name" + desc), null when the class has none */
    public static java.util.Map<String, Integer> postsPerMethod(ClassNode cn) {
        java.util.Map<String, Integer> out = null;
        for (MethodNode m : cn.methods)
            for (AbstractInsnNode in : m.instructions)
                if (isPost(in)) {
                    if (out == null) out = new java.util.HashMap<>();
                    out.merge(m.name + m.desc, 1, Integer::sum);
                }
        return out;
    }

    /**
     * The methods and the mods (their mixin configs) in them, for a player's log: "onLivingTick (coo.mixins.json)".
     * (A VerifyError on a player's server named a method; which mod wrapped it latest.log did not say.)
     */
    private static String named(String what, java.util.Map<String, java.util.Set<String>> named) {
        if (named.isEmpty()) return "";
        java.util.Map<String, java.util.List<String>> byMods = new java.util.TreeMap<>();   // one mod in many methods: one entry
        named.forEach((m, mods) -> byMods.computeIfAbsent(mods.isEmpty() ? "?" : String.join(", ", mods), k -> new java.util.ArrayList<>()).add(m));
        StringBuilder b = new StringBuilder("; " + what);
        byMods.forEach((mods, ms) -> b.append(ms.size() > 8 ? String.join(", ", ms.subList(0, 8)) + " +" + (ms.size() - 8) : String.join(", ", ms))
                .append(" (").append(mods).append(") "));
        return b.toString().stripTrailing();
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
