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

    /** The class's node (flags as ClassReader.accept's), or null when the resource is not there. */
    public static ClassNode read(ClassLoader loader, String internalName, int flags) throws java.io.IOException {
        try (InputStream in = (loader != null ? loader : ClassLoader.getSystemClassLoader()).getResourceAsStream(internalName + ".class")) {
            if (in == null) return null;
            ClassNode cn = new ClassNode();
            ClassReader r = new ClassReader(in);
            if (Names.SRG) r.accept(new ClassRemapper(cn, OFFICIAL), flags);
            else r.accept(cn, flags);
            return cn;
        }
    }
}
