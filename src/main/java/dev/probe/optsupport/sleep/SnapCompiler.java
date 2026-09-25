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
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
        cw.visitEnd();
        Class<?> c = MethodHandles.lookup().defineHiddenClassWithClassData(cw.toByteArray(), vhs, true).lookupClass();
        return (Snapper) c.getDeclaredConstructor().newInstance();
    }

    private static void pushInt(MethodVisitor m, int v) {
        if (v <= 5) m.visitInsn(Opcodes.ICONST_0 + v);
        else if (v <= 127) m.visitIntInsn(Opcodes.BIPUSH, v);
        else m.visitIntInsn(Opcodes.SIPUSH, v);
    }

    /** Fallback: reflection (slow, but always works). */
    private static Snapper reflective(List<Field> fields) {
        Field[] fs = fields.toArray(new Field[0]);
        return (be, out) -> {
            for (int i = 0; i < fs.length; i++) {
                try {
                    Class<?> t = fs[i].getType();
                    if (t == int.class) out[i] = fs[i].getInt(be);
                    else if (t == long.class) out[i] = fs[i].getLong(be);
                    else if (t == boolean.class) out[i] = fs[i].getBoolean(be) ? 1 : 0;
                    else if (t == short.class) out[i] = fs[i].getShort(be);
                    else if (t == byte.class) out[i] = fs[i].getByte(be);
                    else if (t == float.class) out[i] = Float.floatToRawIntBits(fs[i].getFloat(be));
                    else if (t == double.class) out[i] = Double.doubleToRawLongBits(fs[i].getDouble(be));
                    else out[i] = FieldDiff.fingerprint(fs[i].get(be));
                } catch (IllegalAccessException e) {
                    out[i] = 0;
                }
            }
        };
    }
}
