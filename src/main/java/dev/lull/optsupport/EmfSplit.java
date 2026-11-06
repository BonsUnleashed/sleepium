package dev.lull.optsupport;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;

/**
 * EMF's generated animation class, cut (HugeMethods) and checked before EMF defines it: the cut class is defined in a
 * throwaway loader and initialised (the JVM verifies it); on any failure EMF gets its own bytes.
 * -Dlab.hugeMethods.check=true: both versions also run eval on the same random inputs, outputs compared bit for bit.
 */
public final class EmfSplit {
    private static final boolean CHECK = Boolean.getBoolean("lab.hugeMethods.check");
    public static final AtomicInteger classes = new AtomicInteger(), cut = new AtomicInteger(), rejected = new AtomicInteger(),
            checked = new AtomicInteger(), differed = new AtomicInteger();
    private static final AtomicInteger logged = new AtomicInteger();

    private EmfSplit() {}

    public static byte[] split(byte[] bytes, ClassLoader parent) {
        classes.incrementAndGet();
        byte[] out = HugeMethods.split(bytes);
        if (out == bytes) return bytes;
        String name = new ClassReader(out).getClassName().replace('/', '.');
        try {
            Class<?> c = define(parent, name, out);
            if (CHECK) check(define(parent, name, bytes), c, name);
        } catch (Throwable t) {
            rejected.incrementAndGet();
            if (logged.getAndIncrement() < 5) System.out.println("[sleep/opt] EMF animation " + name + " left uncut: " + t);
            return bytes;
        }
        if (cut.incrementAndGet() == 1) System.out.println("[sleep/opt] EMF animations cut into parts the JIT compiles (first: " + name + ")");
        return out;
    }

    private static Class<?> define(ClassLoader parent, String name, byte[] b) throws Exception {
        Loader l = new Loader(parent);
        Class<?> c = l.def(name, b);
        Class.forName(name, true, l);   // linked and initialised: verified
        return c;
    }

    /** a throwaway loader under EMF's: the generated class' references (EMF's math) resolve as in EMF's own */
    private static final class Loader extends ClassLoader {
        Loader(ClassLoader parent) {
            super(parent);
        }

        Class<?> def(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    /** eval of both on the same random inputs: the arrays after it, bit for bit */
    private static void check(Class<?> original, Class<?> split, String name) throws Throwable {
        MethodType t = MethodType.methodType(void.class, float[].class, boolean[].class);
        MethodHandle a = MethodHandles.lookup().findStatic(original, "eval", t), b = MethodHandles.lookup().findStatic(split, "eval", t);
        SplittableRandom r = new SplittableRandom(name.hashCode());
        for (int round = 0; round < 64; round++) {
            float[] fa = new float[4096];
            boolean[] ba = new boolean[4096];
            for (int i = 0; i < fa.length; i++) {
                fa[i] = round % 4 == 0 ? (float) r.nextInt(-3, 4) : (float) (r.nextDouble() * 200 - 100);
                ba[i] = r.nextBoolean();
            }
            float[] fb = fa.clone();
            boolean[] bb = ba.clone();
            Throwable ea = null, eb = null;
            try {
                a.invokeExact(fa, ba);
            } catch (Throwable e) {
                ea = e;
            }
            try {
                b.invokeExact(fb, bb);
            } catch (Throwable e) {
                eb = e;
            }
            boolean same = (ea == null) == (eb == null) && (ea == null || ea.getClass() == eb.getClass()) && java.util.Arrays.equals(ba, bb);
            for (int i = 0; same && i < fa.length; i++) same = Float.floatToRawIntBits(fa[i]) == Float.floatToRawIntBits(fb[i]);
            checked.incrementAndGet();
            if (!same) {
                differed.incrementAndGet();
                throw new IllegalStateException("cut eval differs from EMF's in round " + round);
            }
        }
    }

    public static String report() {
        return "EMF animations: classes " + classes.get() + ", cut " + cut.get() + ", left uncut " + rejected.get()
                + (CHECK ? ", checked " + checked.get() + " runs, differed " + differed.get() : "") + "; " + HugeMethods.report();
    }
}
