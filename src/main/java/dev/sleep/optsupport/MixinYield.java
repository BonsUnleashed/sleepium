package dev.sleep.optsupport;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The one case where the engine gives way to another mod: an @Overwrite of ours on a method another mod's mixin
 * changes. Mixin cannot inject into a method merged by an overwrite (a load error: "cannot inject into ... merged by"),
 * and an overwrite of theirs would drop ours or theirs anyway. Then our mixin is not applied (logged), and the method
 * runs vanilla's code with their change. The same for a @Redirect of ours on a call another mod's mixin redirects
 * (two redirects of one call: the second fails to load). Everything else of ours stays.
 */
public final class MixinYield {
    private MixinYield() {}

    public static long yielded;

    /** True when our mixin overwrites a method of the target that a foreign mixin touches. */
    public static boolean yields(String targetClassName, String mixinClassName) {
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
