package dev.probe.optsupport;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * Which methods of a class other mods' mixins change, per method name. The bytecode analysers (sleep engine, shape
 * purity) read classes from their resources, i.e. before mixins; this tells them where those bytes are no longer the
 * code that runs, so they can refuse just those methods instead of the whole class.
 *
 * <p>A method name counts as touched by a mixin when the mixin
 * <ul>
 *   <li>injects into it: any method annotation with a {@code method} attribute (Inject, Redirect, ModifyArg(s),
 *       ModifyVariable, ModifyConstant, and MixinExtras' Wrap/Modify* injectors), a wildcard meaning every method;</li>
 *   <li>overwrites it (@Overwrite), or merges a method of that name (unannotated or @Unique: it may override an
 *       inherited one); @Shadow, @Accessor and @Invoker change no existing code and are ignored.</li>
 * </ul>
 * Mixins of this mod (dev.probe.*) are ignored: they are exactness-checked. Pending mixins of not yet loaded classes
 * are included. Any failure to read a mixin makes every method of its target count as touched.
 */
public final class MixinTouch {
    private static final String ALL = "*";
    private static final Map<String, Set<String>> TOUCHED = new ConcurrentHashMap<>();
    private static final Field PENDING = pendingField();

    private MixinTouch() {}

    /** Is method {@code name} of class {@code internalName} changed by some foreign mixin? */
    public static boolean touched(String internalName, String name) {
        Set<String> t = TOUCHED.computeIfAbsent(internalName, MixinTouch::compute);
        return t.contains(ALL) || t.contains(name);
    }

    /** Any foreign mixin at all on this class (for callers that cannot work per method). */
    public static boolean anyForeign(String internalName) {
        return !TOUCHED.computeIfAbsent(internalName, MixinTouch::compute).isEmpty();
    }

    private static Set<String> compute(String internalName) {
        Set<String> out = new HashSet<>();
        try {
            ClassInfo info = ClassInfo.fromCache(internalName);
            if (info == null) return Set.of();
            Set<IMixinInfo> mixins = new HashSet<>(info.getAppliedMixins());
            if (PENDING != null) {
                Object p = PENDING.get(info);
                if (p instanceof Collection<?> c) for (Object o : c) if (o instanceof IMixinInfo mi) mixins.add(mi);
            }
            for (IMixinInfo mi : mixins) {
                if (mi.getClassName().startsWith("dev.probe.")) continue;
                collect(mi, out);
            }
        } catch (Throwable t) {
            out.add(ALL);
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    private static void collect(IMixinInfo mi, Set<String> out) {
        ClassNode cn;
        try {
            cn = mi.getClassNode(0);
        } catch (Throwable t) {
            cn = null;
        }
        if (cn == null) {
            out.add(ALL);
            return;
        }
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<clinit>")) continue;
            boolean injector = false, passive = false;
            for (AnnotationNode a : annotations(m)) {
                String d = a.desc;
                if (d.endsWith("/Shadow;") || d.endsWith("/Accessor;") || d.endsWith("/Invoker;")) passive = true;
                if (d.endsWith("/Overwrite;")) out.add(m.name);
                boolean injectorType = d.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !d.endsWith("/At;") && !d.endsWith("/Slice;")
                        || d.startsWith("Lcom/llamalad7/mixinextras/injector/");
                boolean named = false;
                List<Object> v = a.values;
                if (v != null) {
                    for (int i = 0; i + 1 < v.size(); i += 2) {
                        if (!"method".equals(v.get(i))) continue;
                        named = injector = true;
                        Object targets = v.get(i + 1);
                        if (targets instanceof List<?> l) for (Object s : l) out.add(nameOf(String.valueOf(s)));
                        else out.add(nameOf(String.valueOf(targets)));
                    }
                }
                if (injectorType && !named) {   // targets given another way (@Desc): cannot tell which method
                    injector = true;
                    out.add(ALL);
                }
            }
            if (!injector && !passive && !m.name.startsWith("lambda$")) out.add(m.name.equals("<init>") ? "<init>" : m.name);
        }
    }

    /** "name", "name(desc)ret", "Lowner;name(desc)ret" or a pattern -> the method name, or ALL if unclear. */
    static String nameOf(String target) {
        String s = target.trim();
        int semi = s.indexOf(';'), paren = s.indexOf('(');
        if (s.startsWith("L") && semi > 0 && (paren < 0 || semi < paren)) s = s.substring(semi + 1);
        int end = s.length();
        for (char c : new char[] {'(', ':'}) {
            int k = s.indexOf(c);
            if (k >= 0 && k < end) end = k;
        }
        s = s.substring(0, end);
        return s.isEmpty() || s.contains("*") || s.contains("/") || s.contains(" ") ? ALL : s;
    }

    private static List<AnnotationNode> annotations(MethodNode m) {
        List<AnnotationNode> all = new java.util.ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        return all;
    }

    private static Field pendingField() {
        try {
            Field f = ClassInfo.class.getDeclaredField("mixins");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Diagnostics: the touched method names of a class. */
    public static Set<String> touchedMethods(String internalName) {
        return TOUCHED.computeIfAbsent(internalName, MixinTouch::compute);
    }
}
