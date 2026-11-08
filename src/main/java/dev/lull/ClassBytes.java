package dev.lull;

import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/**
 * Class bytes for the engine's bytecode analyses (sleep, shape purity, escape, move memo), read as the class's
 * resource - before mixins - with Mojang's member names: in production Forge 1.20.1's classes carry SRG member names
 * (m_12345_, f_12345_, lambda$m_12345_$0), turned back here (Names.official), so every analysis compares names as it
 * does on 1.21.1. What an analysis then looks up at run time goes through Names.field / Names.method / Names.official.
 */
public final class ClassBytes {
    private ClassBytes() {}

    private static final Remapper OFFICIAL = new Remapper() {
        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return official(name);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return Names.official(name);
        }

        @Override
        public String mapInvokeDynamicMethodName(String name, String descriptor) {
            return official(name);
        }
    };

    /** m_x_ and lambda$m_x_$N (a lambda's synthetic method named after its SRG enclosing method) */
    private static String official(String name) {
        if (name.startsWith("lambda$")) {
            int end = name.lastIndexOf('$');
            if (end > 7) return "lambda$" + Names.official(name.substring(7, end)) + name.substring(end);
        }
        return Names.official(name);
    }

    /** The class's node before mixins (flags as ClassReader.accept's), or null when the resource is not there. */
    public static ClassNode readBefore(ClassLoader loader, String internalName, int flags) throws java.io.IOException {
        try (InputStream in = (loader != null ? loader : ClassLoader.getSystemClassLoader()).getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassNode cn = new ClassNode();
            ClassReader r = new ClassReader(in);
            if (Names.SRG) r.accept(new ClassRemapper(cn, OFFICIAL), flags);
            else r.accept(cn, flags);
            return complete(cn);
        }
    }

    /**
     * A node's method lists as the analyses expect them. Stock ASM creates every method's lists; a mod that patches ASM to
     * save memory may leave an empty one null (an I3AT5 pack: "method.tryCatchBlocks is null" in Analyzer, every entity
     * of two mods refused). A list null there was never filled - ClassWriter could not write the method otherwise.
     */
    public static ClassNode complete(ClassNode cn) {
        for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
            if (m.tryCatchBlocks == null) m.tryCatchBlocks = new java.util.ArrayList<>(0);
            if (m.localVariables == null) m.localVariables = new java.util.ArrayList<>(0);
            if (m.exceptions == null) m.exceptions = new java.util.ArrayList<>(0);
        }
        return cn;
    }

    /**
     * The class's node (flags as ClassReader.accept's), or null when the resource is not there; with
     * lab.analysis.running, what other mods' mixins change in it as it runs (readRunning).
     */
    public static ClassNode read(ClassLoader loader, String internalName, int flags) throws java.io.IOException {
        ClassNode cn = readBefore(loader, internalName, flags);
        if (cn == null) return null;
        if (RUNNING && dev.lull.optsupport.MixinTouch.anyForeign(internalName)
                && readRunning(loader, internalName, cn, name -> dev.lull.optsupport.MixinTouch.touched(internalName, name)) != null)
            MERGED.add(internalName);   // other mods' mixins in it: what they change, as it runs
        return cn;
    }

    /**
     * An analyzer run on code whose max stack / locals may be too small: other mods' mixins leave them to the class
     * writer (transformClass hands the pre-mixin values on), and ASM's COMPUTE_MAXS cannot size it either - in code
     * without frames it undersizes a catch handler (Entity.tryCheckInsideBlocks: 2 where NEW DUP ALOAD needs 3). The
     * sizes only bound the analyzer's frames, so they grow until the analysis fits. (Collections Of Optimizations: 15
     * entity classes' analyses failed with "Insufficient maximum stack size", their entities never slept.)
     */
    public static <V extends org.objectweb.asm.tree.analysis.Value> org.objectweb.asm.tree.analysis.Frame<V>[] analyze(
            org.objectweb.asm.tree.analysis.Analyzer<V> an, String owner, org.objectweb.asm.tree.MethodNode m) throws org.objectweb.asm.tree.analysis.AnalyzerException {
        for (int grown = 0; ; grown++) {
            try {
                return an.analyze(owner, m);
            } catch (org.objectweb.asm.tree.analysis.AnalyzerException e) {
                String msg = String.valueOf(e.getMessage());
                boolean stack = msg.contains("Insufficient maximum stack size");
                if (!stack && !msg.contains("inexistant local variable") || grown == 12) throw e;
                if (stack) m.maxStack = Math.min(0xFFFF, Math.max(m.maxStack * 2, m.maxStack + 4));
                else m.maxLocals = Math.min(0xFFFF, Math.max(m.maxLocals * 2, m.maxLocals + 4));
            }
        }
    }

    /** set while readRunning puts a class through Mixin's transformer again (a copy for the analyses, not the class) */
    public static final ThreadLocal<Boolean> REREAD = new ThreadLocal<>();

    /** classes read with what other mods' mixins change in them as it runs (readRunning) */
    private static final java.util.Set<String> MERGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Is this method changed by another mod's mixin in a way the analyses cannot see - its class read before mixins
     * (or not read yet)? With -Dlab.analysis.running the analyses see such methods as they run.
     */
    public static boolean unseen(String owner, String name) {
        return dev.lull.optsupport.MixinTouch.touched(owner, name) && !MERGED.contains(owner);
    }

    /**
     * -Dlab.analysis.running=true: for a class other mods' mixins change, the analyses read the code that runs instead
     * of refusing what those mixins touch.
     */
    public static final boolean RUNNING = Boolean.getBoolean("lab.analysis.running");

    /**
     * The class before mixins ({@code before}, as read) with the methods other mods' mixins change ({@code foreign}, by
     * name) as they run, and the methods mixins add (handlers, merged methods); this mod's own changes elsewhere stay out,
     * as in the resource bytes. Null when the running code is not at hand.
     */
    public static ClassNode readRunning(ClassLoader loader, String internalName, ClassNode before, java.util.function.Predicate<String> foreign) {
        ClassNode run = readRunning(loader, internalName);
        if (run == null || before == null) return null;
        java.util.Map<String, org.objectweb.asm.tree.MethodNode> had = new java.util.HashMap<>();
        for (org.objectweb.asm.tree.MethodNode m : before.methods) had.put(m.name + m.desc, m);
        for (org.objectweb.asm.tree.MethodNode m : run.methods) {
            org.objectweb.asm.tree.MethodNode b = had.get(m.name + m.desc);
            if (b == null) before.methods.add(m);
            else if (foreign.test(m.name)) before.methods.set(before.methods.indexOf(b), m);
        }
        for (String i : run.interfaces) if (!before.interfaces.contains(i)) before.interfaces.add(i);   // a mixin's interface, its default methods
        java.util.Set<String> fields = new java.util.HashSet<>();
        for (org.objectweb.asm.tree.FieldNode f : before.fields) fields.add(f.name);
        for (org.objectweb.asm.tree.FieldNode f : run.fields) if (fields.add(f.name)) before.fields.add(f);
        return before;
    }

    /**
     * The class as it runs: its resource bytes put through Mixin's transformer again (every mod's mixins applied as when
     * the class was loaded; Mixin's applyMixins is synchronized), Mojang's names, no frames. Null when the resource or
     * Mixin's transformer is not at hand, or the transformer fails.
     */
    public static ClassNode readRunning(ClassLoader loader, String internalName) {
        try (InputStream in = (loader != null ? loader : ClassLoader.getSystemClassLoader()).getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            org.spongepowered.asm.mixin.MixinEnvironment env = org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment();
            if (!(env.getActiveTransformer() instanceof org.spongepowered.asm.mixin.transformer.IMixinTransformer t)) return null;
            ClassNode raw = new ClassNode();
            new ClassReader(in).accept(raw, ClassReader.EXPAND_FRAMES);
            REREAD.set(Boolean.TRUE);
            try {
                t.transformClass(env, internalName.replace('/', '.'), raw);
            } finally {
                REREAD.remove();
            }
            ClassNode cn = new ClassNode();
            raw.accept(Names.SRG ? new ClassRemapper(cn, OFFICIAL) : cn);
            for (org.objectweb.asm.tree.MethodNode m : cn.methods)
                for (org.objectweb.asm.tree.AbstractInsnNode i : m.instructions.toArray())
                    if (i instanceof org.objectweb.asm.tree.FrameNode) m.instructions.remove(i);
            return complete(cn);
        } catch (Throwable e) {
            System.out.println("[sleep/analysis] " + internalName + ": the running code not at hand: " + e);
            return null;
        }
    }
}
