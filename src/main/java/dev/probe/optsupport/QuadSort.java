package dev.probe.optsupport;

import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Vector3f;

/**
 * exp/quad-sort (client): VertexSorting.byDistance's sort, the same permutation without the comparator merge sort.
 *
 * <p>Vanilla computes one float per quad with the distance function, then {@code IntArrays.mergeSort} of the indices
 * with {@code Floats.compare(d[b], d[a])}: descending by {@code Float.compare}, stable (equal distances keep index
 * order). Here the same floats become radix keys: {@code floatToIntBits} (NaN canonical, as Float.compare uses), the
 * sign-flip that makes signed-float order an unsigned-int order ({@code -0.0 < 0.0}, NaN last), then inverted for
 * descending; a stable LSD radix sort over those keys yields exactly the stable descending order. Byte passes whose
 * byte is the same for every key are skipped. Every VertexSorting.byDistance user gets it: immediate translucent
 * buffers (item frames, particles, entities), chunk translucency resorts, mods that use the vanilla sorting.
 *
 * <p>{@code -Dprobe.quadSortCheck=true}: every call also runs vanilla's sort and counts differences.
 */
public final class QuadSort {
    private static final boolean CHECK = Boolean.getBoolean("probe.quadSortCheck");
    public static volatile long sorts, quads, checked, mismatches;

    private QuadSort() {}

    private static final ThreadLocal<int[][]> SCRATCH = ThreadLocal.withInitial(() -> new int[][] {new int[0], new int[0], new int[0], new int[256]});

    /** The body of vanilla's byDistance(DistanceFunction) lambda. */
    public static int[] byDistance(VertexSorting.DistanceFunction f, Vector3f[] v) {
        int n = v.length;
        float[] d = new float[n];
        for (int i = 0; i < n; i++) d[i] = f.apply(v[i]);
        int[] out = sort(d);
        sorts++;
        quads += n;
        if (CHECK) {
            int[] ref = new int[n];
            for (int i = 0; i < n; i++) ref[i] = i;
            IntArrays.mergeSort(ref, (a, b) -> com.google.common.primitives.Floats.compare(d[b], d[a]));
            checked++;
            if (!java.util.Arrays.equals(ref, out)) mismatches++;
        }
        return out;
    }

    /** Indices of d sorted by Float.compare descending, stable. */
    static int[] sort(float[] d) {
        int n = d.length;
        int[] out = new int[n];
        if (n < 32) {   // stable insertion sort: any stable sort gives the same permutation
            for (int i = 0; i < n; i++) {
                int j = i;
                float x = d[i];
                while (j > 0 && Float.compare(d[out[j - 1]], x) < 0) {
                    out[j] = out[j - 1];
                    j--;
                }
                out[j] = i;
            }
            return out;
        }
        int[][] s = SCRATCH.get();
        if (s[0].length < n) {
            int c = Math.max(n, s[0].length * 2);
            s[0] = new int[c];
            s[1] = new int[c];
            s[2] = new int[c];
        }
        int[] keys = s[0], tmpKeys = s[1], tmpIdx = s[2], count = s[3];
        int and = -1, or = 0;
        for (int i = 0; i < n; i++) {
            int b = Float.floatToIntBits(d[i]);
            int k = ~(b ^ ((b >> 31) | 0x80000000));   // ascending unsigned order of floats, inverted: descending
            keys[i] = k;
            out[i] = i;
            and &= k;
            or |= k;
        }
        int[] srcK = keys, srcI = out, dstK = tmpKeys, dstI = tmpIdx;
        for (int shift = 0; shift < 32; shift += 8) {
            if ((((and ^ or) >>> shift) & 0xFF) == 0) continue;   // same byte everywhere: order unchanged
            java.util.Arrays.fill(count, 0);
            for (int i = 0; i < n; i++) count[(srcK[i] >>> shift) & 0xFF]++;
            for (int b = 0, acc = 0; b < 256; b++) {
                int c = count[b];
                count[b] = acc;
                acc += c;
            }
            for (int i = 0; i < n; i++) {
                int k = srcK[i];
                int p = count[(k >>> shift) & 0xFF]++;
                dstK[p] = k;
                dstI[p] = srcI[i];
            }
            int[] t = srcK; srcK = dstK; dstK = t;
            t = srcI; srcI = dstI; dstI = t;
        }
        if (srcI != out) System.arraycopy(srcI, 0, out, 0, n);
        return out;
    }

    /** Replaces the body of VertexSorting's byDistance lambda with a call to {@link #byDistance} (from OptMixinPlugin). */
    public static void transform(org.objectweb.asm.tree.ClassNode cn) {
        String desc = "(Lcom/mojang/blaze3d/vertex/VertexSorting$DistanceFunction;[Lorg/joml/Vector3f;)[I";
        for (var m : cn.methods) {
            if (!m.desc.equals(desc) || (m.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0 || !m.name.startsWith("lambda$")) continue;
            boolean vanilla = false;
            for (var in : m.instructions)
                if (in instanceof org.objectweb.asm.tree.MethodInsnNode mi && mi.owner.equals("it/unimi/dsi/fastutil/ints/IntArrays") && mi.name.equals("mergeSort")) vanilla = true;
            if (!vanilla) continue;   // someone else's body: left alone
            m.instructions.clear();
            m.tryCatchBlocks.clear();
            if (m.localVariables != null) m.localVariables.clear();
            m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 0));
            m.instructions.add(new org.objectweb.asm.tree.VarInsnNode(org.objectweb.asm.Opcodes.ALOAD, 1));
            m.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, "dev/probe/optsupport/QuadSort", "byDistance", desc, false));
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
            m.maxStack = 2;
            m.maxLocals = 2;
            System.out.println("[probe/opt] quad sort: " + cn.name + "." + m.name + " -> QuadSort.byDistance");
        }
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "quad sorts %d (%d quads)%s", sorts, quads,
                CHECK ? ", checked " + checked + ", mismatches " + mismatches : "");
    }
}
