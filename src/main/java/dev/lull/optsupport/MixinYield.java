package dev.lull.optsupport;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The one case where the engine gives way to another mod: an @Overwrite of ours on a method another mod's mixin
 * changes. Mixin cannot inject into a method merged by an overwrite (a load error: "cannot inject into ... merged by"),
 * and an overwrite of theirs would drop ours or theirs anyway. Then our mixin is not applied (logged), and the method
 * runs vanilla's code with their change. The same for a @Redirect of ours on a call another mod's mixin redirects
 * (two redirects of one call: the second fails to load), and for any injector of ours into a method another mod's
 * mixin overwrites (Sodium's renderSectionLayer). Everything else of ours stays.
 */
public final class MixinYield {
    private MixinYield() {}

    public static long yielded;

    private static final java.util.Map<String, Boolean> DECIDED = new java.util.concurrent.ConcurrentHashMap<>();

    /** True when our mixin overwrites a method of the target that a foreign mixin touches (decided once per mixin). */
    public static boolean yields(String targetClassName, String mixinClassName) {
        Boolean d = DECIDED.get(mixinClassName);
        if (d == null) DECIDED.put(mixinClassName, d = yields0(targetClassName, mixinClassName));
        return d;
    }

    /** our mixins that add an interface of ours to their target: interface (internal name) -> {mixin class, target class} */
    private static volatile java.util.Map<String, String[]> PROVIDERS;

    /**
     * True when this mixin of ours uses (casts to, checks, calls) an interface another mixin of ours adds, and that one
     * gives way to another mod: applied alone it would find the interface missing (ShapeCacheMixin gave way to
     * ModernFix's getCollisionShape and BlockCollisionMemoMixin's casts failed).
     */
    public static boolean needsYielded(String mixinClassName) {
        ClassNode cn;
        try {
            cn = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);
        } catch (Throwable t) {
            return false;
        }
        java.util.Map<String, String[]> providers = providers();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (MethodNode m : cn.methods)
            for (org.objectweb.asm.tree.AbstractInsnNode in = m.instructions.getFirst(); in != null; in = in.getNext()) {
                if (in instanceof org.objectweb.asm.tree.TypeInsnNode t) used.add(t.desc);
                else if (in instanceof org.objectweb.asm.tree.MethodInsnNode mi && mi.getOpcode() == org.objectweb.asm.Opcodes.INVOKEINTERFACE) used.add(mi.owner);
            }
        for (String type : used) {
            String[] p = providers.get(type);
            if (p == null || p[0].equals(mixinClassName)) continue;
            if (yields(p[1], p[0])) {
                yielded++;
                System.out.println("[sleep/opt] " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " not applied: it needs "
                        + type.substring(type.lastIndexOf('/') + 1) + " from " + p[0].substring(p[0].lastIndexOf('.') + 1) + ", which gave way");
                return true;
            }
        }
        return false;
    }

    private static java.util.Map<String, String[]> providers() {
        java.util.Map<String, String[]> m = PROVIDERS;
        if (m != null) return m;
        m = new java.util.HashMap<>();
        try {
            var provider = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider();
            for (org.spongepowered.asm.mixin.transformer.Config c : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
                org.spongepowered.asm.mixin.extensibility.IMixinConfig mc = c.getConfig();
                if (mc == null || !mc.getName().startsWith("sleepium")) continue;
                String pkg = mc.getMixinPackage();
                if (!pkg.endsWith(".")) pkg += ".";
                for (String field : new String[] {"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
                    java.lang.reflect.Field f = mc.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    if (!(f.get(mc) instanceof java.util.List<?> names)) continue;
                    for (Object n : names) {
                        String cls = pkg + n;
                        ClassNode cn;
                        try {
                            cn = provider.getClassNode(cls);
                        } catch (Throwable t) {
                            continue;
                        }
                        java.util.List<String> targets = MixinTouch.targets(cn);
                        if (targets.isEmpty() || cn.interfaces == null) continue;
                        for (String i : cn.interfaces)
                            if (i.startsWith("dev/lull/")) m.putIfAbsent(i, new String[] {cls, targets.get(0).replace('/', '.')});
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[sleep/opt] mixin interface providers incomplete: " + t);
        }
        PROVIDERS = m;
        return m;
    }

    private static boolean yields0(String targetClassName, String mixinClassName) {
        ClassNode cn;
        try {
            cn = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);
        } catch (Throwable t) {
            if (Boolean.getBoolean("sleep.mixinIndex.debug")) System.out.println("[sleep/opt] yield check: cannot read " + mixinClassName + ": " + t);
            return false;
        }
        String target = targetClassName.replace('.', '/');
        for (String key : MixinTouch.redirectKeys(cn)) {   // a @Redirect of ours on a call another mod redirects too: one of them fails to load
            int bar = key.indexOf('|');
            if (MixinTouch.redirected(target, key.substring(0, bar), key.substring(bar + 1))) {
                yielded++;
                System.out.println("[sleep/opt] " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " not applied: the call to "
                        + key.substring(bar + 1) + " in " + targetClassName + "#" + key.substring(0, bar) + " is redirected by another mod's mixin");
                return true;
            }
        }
        for (MethodNode m : cn.methods)   // an injector of ours into a method another mod overwrites: Mixin refuses it at load
            for (String t : MixinTouch.injectorTargets(m))
                if (MixinTouch.overwritten(target, t)) {
                    yielded++;
                    System.out.println("[sleep/opt] " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " not applied: " + targetClassName + "#" + t
                            + " is overwritten by another mod's mixin (our injector cannot go into it)");
                    return true;
                }
        for (MethodNode m : cn.methods) {
            if (!overwrite(m)) continue;
            if (Boolean.getBoolean("sleep.mixinIndex.debug"))
                System.out.println("[sleep/opt] yield check: " + mixinClassName + " overwrites " + target + "#" + m.name + " touched=" + MixinTouch.touched(target, m.name));
            if (MixinTouch.touched(target, m.name)) {
                yielded++;
                System.out.println("[sleep/opt] " + mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1) + " not applied: "
                        + targetClassName + "#" + m.name + " is changed by another mod's mixin (our overwrite would break it)");
                return true;
            }
        }
        return false;
    }

    private static boolean overwrite(MethodNode m) {
        for (java.util.List<AnnotationNode> l : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations))
            if (l != null) for (AnnotationNode a : l) if ("Lorg/spongepowered/asm/mixin/Overwrite;".equals(a.desc)) return true;
        return false;
    }
}
