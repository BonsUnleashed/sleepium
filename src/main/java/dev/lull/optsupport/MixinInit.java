package dev.lull.optsupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field initialisers of mixins as plain calls: Forge 1.20.1's Mixin (0.8.5) refuses a jump in a mixin's initialiser
 * ("Unsupported JUMP opcode in initialiser": a ternary, && or ||), which 1.21.1's accepts.
 */
public final class MixinInit {
    private MixinInit() {}

    /** true if any of the flags is set (-Dname=true) */
    public static boolean anyFlag(String... names) {
        for (String n : names) if (Boolean.getBoolean(n)) return true;
        return false;
    }

    /** a new concurrent map when on, else null */
    public static <K, V> Map<K, V> mapIf(boolean on) {
        return on ? new ConcurrentHashMap<>() : null;
    }
}
