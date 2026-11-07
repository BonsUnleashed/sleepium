package dev.lull.optsupport;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * Our hooks in the methods that override a hooked one. A mixin injects into owner.m; a subclass that overrides m without
 * calling super.m on every path never runs that hook - the game's own subclass, or a method another mod's mixin adds to
 * one (ModernFix adds MinecraftServer.waitForTasks on 1.20.1: CondWake's "parking" flag was never set there, tell()
 * skipped the wake-up, and every task handed to the server waited for the next tick, 23 ms on average). A hook
 * registered here (a static call at the start and before every return, no arguments) is added to every such override
 * at the end of its class' transformation (LateEventSites' postApply: after every mod's mixins), so it runs whoever
 * wrote the method that actually runs.
 */
public final class OverrideHooks {
    /** owner (internal name), method names (dev and SRG), descriptor; head / ret: static ()V methods "owner.name" */
    record Hook(String feature, String owner, Set<String> names, String desc, String head, String ret) {}

    private static final List<Hook> HOOKS = new CopyOnWriteArrayList<>();

    private OverrideHooks() {}

    public static void add(String feature, String owner, Set<String> names, String desc, String head, String ret) {
        HOOKS.add(new Hook(feature, owner, names, desc, head, ret));
    }

    public static boolean any() {
        return !HOOKS.isEmpty();
    }

    /** a class at the end of its transformation: its overrides of hooked methods get the hooks */
    static void apply(ClassNode cn) {
        if (HOOKS.isEmpty()) return;
        for (Hook h : HOOKS) {
            if (cn.name.equals(h.owner)) continue;
            MethodNode found = null;
            for (MethodNode m : cn.methods)
                if (h.names.contains(m.name) && m.desc.equals(h.desc) && (m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE)) == 0) found = m;
            if (found == null || !isSubclass(cn, h.owner)) continue;
            insert(found, h);
            System.out.println("[sleep/opt] " + h.feature + ": hooked " + cn.name.replace('/', '.') + "." + found.name
                    + " too (it overrides " + h.owner.substring(h.owner.lastIndexOf('/') + 1) + "'s, maybe through another mod's mixin)");
        }
    }

    private static boolean isSubclass(ClassNode cn, String owner) {
        if (owner.equals(cn.superName)) return true;
        try {
            ClassInfo info = ClassInfo.forName(cn.superName);
            return info != null && (info.getName().equals(owner) || info.hasSuperClass(owner));
        } catch (Throwable e) {
            return false;
        }
    }

    private static void insert(MethodNode m, Hook h) {
        InsnList ins = m.instructions;
        for (AbstractInsnNode in : ins.toArray()) {
            int op = in.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) ins.insertBefore(in, call(h.ret));
        }
        ins.insert(call(h.head));
    }

    private static MethodInsnNode call(String target) {
        int dot = target.lastIndexOf('.');
        return new MethodInsnNode(Opcodes.INVOKESTATIC, target.substring(0, dot), target.substring(dot + 1), "()V", false);
    }
}
