package dev.probe.optsupport.sleep;

import dev.probe.FieldDiff;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Compiles, once per block-entity class, a snapshot routine for the fields the sleep engine watches.
 *
 * <p>Field layouts cannot change after classes are loaded (game and mods included), so instead of reflection on every
 * tick a hidden class is generated per block-entity class. It lives in this package and receives the fields'
 * VarHandles as class data, stored in static final fields: for the JIT these are constants, so each read folds to a
 * plain field load. (A nestmate hidden class inside the Minecraft module is not allowed: no full-privilege lookup
 * there.)
 */
final class SnapCompiler {
    /** Writes the watched fields of {@code be} into {@code out} (primitive bits, or a reference/content fingerprint). */
    interface Snapper {
        void snap(Object be, long[] out);

        /** The references of the reference slots (primitive slots left untouched). */
        void refs(Object be, Object[] out);

        /**
         * Cheap check for a sleeping block entity: primitive slots equal {@code prims} (same encoding as snap) and
         * reference slots are the very same objects as {@code refs}. Reads only the block entity's own fields.
         */
        boolean same(Object be, long[] prims, Object[] refs);
    }

    private static final String SNAPPER = Type.getInternalName(Snapper.class);
    private static final String FP_OWNER = Type.getInternalName(FieldDiff.class);
    private static int counter;
    static String failure;

    private SnapCompiler() {}

    /**
     * One hidden class per block-entity class, defined in this package (full privilege here) with the fields'
     * VarHandles as class data, loaded into {@code static final} fields in {@code <clinit>}. A static final VarHandle
     * is a JIT constant, so every read folds to a plain field load, like hand-written GETFIELD code.
     * Falls back to reflection on failure.
     */
    static Snapper compile(List<Field> fields) {
        try {
            List<java.lang.invoke.VarHandle> vhs = new ArrayList<>();
            for (Field f : fields) vhs.add(MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(f));
            return generate(fields, vhs);
        } catch (Throwable t) {
            if (failure == null) failure = t.toString();
            return reflective(fields);
        }
    }

    private static Snapper generate(List<Field> fields, List<java.lang.invoke.VarHandle> vhs) throws Throwable {
        String name = "dev/probe/optsupport/sleep/ProbeSnap" + (counter++);
        String VH = "java/lang/invoke/VarHandle", VHD = "Ljava/lang/invoke/VarHandle;";
        // frames are needed for the branches in same(); every merged type here is Object, so no class loading
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, name, null, "java/lang/Object", new String[] {SNAPPER});
        for (int i = 0; i < fields.size(); i++) cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "VH" + i, VHD, null, null).visitEnd();
        MethodVisitor cl = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        cl.visitCode();
        for (int i = 0; i < fields.size(); i++) {
            cl.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
            cl.visitLdcInsn("_");
            cl.visitLdcInsn(Type.getType(VHD));
            pushInt(cl, i);
            cl.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/invoke/MethodHandles", "classDataAt",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;", false);
            cl.visitTypeInsn(Opcodes.CHECKCAST, VH);
            cl.visitFieldInsn(Opcodes.PUTSTATIC, name, "VH" + i, VHD);
        }
        cl.visitInsn(Opcodes.RETURN);
        cl.visitMaxs(0, 0);
        cl.visitEnd();
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, "snap", "(Ljava/lang/Object;[J)V", null, null);
        m.visitCode();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Class<?> t = f.getType();
            Class<?> owner = f.getDeclaringClass();
            boolean publicOwner = java.lang.reflect.Modifier.isPublic(owner.getModifiers()) && owner.getEnclosingClass() == null;
            String coord = publicOwner ? Type.getDescriptor(owner) : "Ljava/lang/Object;";
            String valueDesc = t.isPrimitive() ? Type.getDescriptor(t) : "Ljava/lang/Object;";
            m.visitVarInsn(Opcodes.ALOAD, 2);
            pushInt(m, i);
            m.visitFieldInsn(Opcodes.GETSTATIC, name, "VH" + i, VHD);
            m.visitVarInsn(Opcodes.ALOAD, 1);
            if (publicOwner) m.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(owner));
            m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, VH, "get", "(" + coord + ")" + valueDesc, false);
            if (t == long.class) {
                // already a long
            } else if (t == int.class || t == short.class || t == byte.class || t == char.class || t == boolean.class) {
                m.visitInsn(Opcodes.I2L);
            } else if (t == float.class) {
                m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
                m.visitInsn(Opcodes.I2L);
            } else if (t == double.class) {
                m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToRawLongBits", "(D)J", false);
            } else {
                m.visitMethodInsn(Opcodes.INVOKESTATIC, FP_OWNER, "fingerprint", "(Ljava/lang/Object;)J", false);
            }
            m.visitInsn(Opcodes.LASTORE);
        }
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        // refs(Object be, Object[] out)
        MethodVisitor r = cw.visitMethod(Opcodes.ACC_PUBLIC, "refs", "(Ljava/lang/Object;[Ljava/lang/Object;)V", null, null);
        r.visitCode();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (f.getType().isPrimitive()) continue;
            r.visitVarInsn(Opcodes.ALOAD, 2);
            pushInt(r, i);
            vhGet(r, name, i, f);
            r.visitInsn(Opcodes.AASTORE);
        }
        r.visitInsn(Opcodes.RETURN);
        r.visitMaxs(0, 0);
        r.visitEnd();
        // same(Object be, long[] prims, Object[] refs)
        MethodVisitor q = cw.visitMethod(Opcodes.ACC_PUBLIC, "same", "(Ljava/lang/Object;[J[Ljava/lang/Object;)Z", null, null);
        q.visitCode();
        org.objectweb.asm.Label differ = new org.objectweb.asm.Label();
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Class<?> t = f.getType();
            if (t.isPrimitive()) {
                vhGet(q, name, i, f);
                toBits(q, t);
                q.visitVarInsn(Opcodes.ALOAD, 2);
                pushInt(q, i);
                q.visitInsn(Opcodes.LALOAD);
                q.visitInsn(Opcodes.LCMP);
                q.visitJumpInsn(Opcodes.IFNE, differ);
            } else {
                vhGet(q, name, i, f);
                q.visitVarInsn(Opcodes.ALOAD, 3);
                pushInt(q, i);
                q.visitInsn(Opcodes.AALOAD);
                q.visitJumpInsn(Opcodes.IF_ACMPNE, differ);
            }
        }
        q.visitInsn(Opcodes.ICONST_1);
        q.visitInsn(Opcodes.IRETURN);
        q.visitLabel(differ);
        q.visitInsn(Opcodes.ICONST_0);
        q.visitInsn(Opcodes.IRETURN);
        q.visitMaxs(0, 0);
        q.visitEnd();
        cw.visitEnd();
        Class<?> c = MethodHandles.lookup().defineHiddenClassWithClassData(cw.toByteArray(), vhs, true).lookupClass();
        return (Snapper) c.getDeclaredConstructor().newInstance();
    }

    /** Push the value of field i of the block entity in local 1 (via the static final VarHandle VHi). */
    private static void vhGet(MethodVisitor m, String name, int i, Field f) {
        Class<?> t = f.getType();
        Class<?> owner = f.getDeclaringClass();
        boolean publicOwner = java.lang.reflect.Modifier.isPublic(owner.getModifiers()) && owner.getEnclosingClass() == null;
        String coord = publicOwner ? Type.getDescriptor(owner) : "Ljava/lang/Object;";
        String valueDesc = t.isPrimitive() ? Type.getDescriptor(t) : "Ljava/lang/Object;";
        m.visitFieldInsn(Opcodes.GETSTATIC, name, "VH" + i, "Ljava/lang/invoke/VarHandle;");
        m.visitVarInsn(Opcodes.ALOAD, 1);
        if (publicOwner) m.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(owner));
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/VarHandle", "get", "(" + coord + ")" + valueDesc, false);
    }

    /** Primitive on the stack -> long bits, the encoding snap uses. */
    private static void toBits(MethodVisitor m, Class<?> t) {
        if (t == long.class) return;
        if (t == float.class) {
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
            m.visitInsn(Opcodes.I2L);
        } else if (t == double.class) {
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToRawLongBits", "(D)J", false);
        } else {
            m.visitInsn(Opcodes.I2L);
        }
    }

    private static void pushInt(MethodVisitor m, int v) {
        if (v <= 5) m.visitInsn(Opcodes.ICONST_0 + v);
        else if (v <= 127) m.visitIntInsn(Opcodes.BIPUSH, v);
        else m.visitIntInsn(Opcodes.SIPUSH, v);
    }

    /** Fallback: reflection (slow, but always works). */
    private static Snapper reflective(List<Field> fields) {
        Field[] fs = fields.toArray(new Field[0]);
        return new Snapper() {
            @Override
            public void snap(Object be, long[] out) {
                for (int i = 0; i < fs.length; i++) out[i] = bits(fs[i], be);
            }

            @Override
            public void refs(Object be, Object[] out) {
                for (int i = 0; i < fs.length; i++) {
                    if (fs[i].getType().isPrimitive()) continue;
                    try {
                        out[i] = fs[i].get(be);
                    } catch (IllegalAccessException e) {
                        out[i] = null;
                    }
                }
            }

            @Override
            public boolean same(Object be, long[] prims, Object[] refs) {
                for (int i = 0; i < fs.length; i++) {
                    try {
                        if (fs[i].getType().isPrimitive() ? bits(fs[i], be) != prims[i] : fs[i].get(be) != refs[i]) return false;
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    private static long bits(Field f, Object be) {
        try {
            Class<?> t = f.getType();
            if (t == int.class) return f.getInt(be);
            if (t == long.class) return f.getLong(be);
            if (t == boolean.class) return f.getBoolean(be) ? 1 : 0;
            if (t == short.class) return f.getShort(be);
            if (t == byte.class) return f.getByte(be);
            if (t == char.class) return f.getChar(be);
            if (t == float.class) return Float.floatToRawIntBits(f.getFloat(be));
            if (t == double.class) return Double.doubleToRawLongBits(f.getDouble(be));
            return FieldDiff.fingerprint(f.get(be));
        } catch (IllegalAccessException e) {
            return 0;
        }
    }
}
