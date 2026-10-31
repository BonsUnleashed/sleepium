package dev.sleep.optsupport;

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
 * <p>{@code -Dsleep.quadSortCheck=true}: every call also runs vanilla's sort and counts differences.
 */
public final class QuadSort {
    private static final boolean CHECK = Boolean.getBoolean("sleep.quadSortCheck");
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

    static int[] sort(float[] d) {
        return sort(d, d.length);
    }

    /** Indices of d[0..n) sorted by Float.compare descending, stable. */
    static int[] sort(float[] d, int n) {
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
            m.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC, "dev/sleep/optsupport/QuadSort", "byDistance", desc, false));
            m.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
            m.maxStack = 2;
            m.maxLocals = 2;
            System.out.println("[sleep/opt] quad sort: " + cn.name + "." + m.name + " -> QuadSort.byDistance");
        }
    }

    // ------------------------------------------------------------------ the sort of BufferSource, without centroid objects

    private static final org.joml.Vector3f ORIGIN = new org.joml.Vector3f();
    private static float[] keys = new float[1024];
    public static long fastSorts, fastQuads, memoHits;

    /** The last sorts by quad count: keys (bit patterns) and their order. */
    private static final class Memo {
        int[] keyBits = new int[0], order;
        int n;
    }

    private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Memo> MEMOS = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

    /**
     * The sort of d[0..n), reused when the same keys (bit for bit) were sorted last time for this quad count: same input,
     * same stable order. A still camera over still objects gives the same keys every frame.
     */
    private static int[] memo(float[] d, int n) {
        if (n < 256) return sort(d, n);
        Memo m = MEMOS.get(n);
        if (m != null) {
            int[] kb = m.keyBits;
            boolean same = true;
            for (int i = 0; i < n; i++) {
                if (Float.floatToRawIntBits(d[i]) != kb[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                memoHits++;
                return m.order;
            }
        } else {
            if (MEMOS.size() > 64) MEMOS.clear();
            MEMOS.put(n, m = new Memo());
        }
        int[] order = sort(d, n);
        if (m.keyBits.length < n) m.keyBits = new int[n];
        for (int i = 0; i < n; i++) m.keyBits[i] = Float.floatToRawIntBits(d[i]);
        m.order = order;
        m.n = n;
        return order;
    }

    /**
     * BufferSource.endBatch: MeshData.sortQuads when the sorting is DISTANCE_TO_ORIGIN (world rendering). The keys come
     * straight from the vertex buffer: the centroid of vertices 0 and 2 as unpackQuadCentroids forms it, the distance
     * by the same JOML call the vanilla lambda makes (origin.distanceSquared); then the same stable descending order
     * and the same index buffer as buildSortedIndexBuffer. No Vector3f per quad, no SortState (BufferSource drops it).
     */
    public static com.mojang.blaze3d.vertex.MeshData.SortState sortQuads(com.mojang.blaze3d.vertex.MeshData m, com.mojang.blaze3d.vertex.ByteBufferBuilder bb,
            VertexSorting sorting) {
        var ds = m.drawState();
        var f = ds.format();
        if (sorting != VertexSorting.DISTANCE_TO_ORIGIN || ds.mode() != com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
                || !f.contains(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)) return m.sortQuads(bb, sorting);
        int quads = ds.vertexCount() / 4;
        if (keys.length < quads) keys = new float[Math.max(quads, keys.length * 2)];
        float[] d = keys;
        long base = org.lwjgl.system.MemoryUtil.memAddress(m.vertexBuffer());
        int stride = f.getVertexSize(), po = f.getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION);
        for (int q = 0; q < quads; q++) {
            long v0 = base + (long) q * 4 * stride + po, v2 = v0 + 2L * stride;
            float cx = (org.lwjgl.system.MemoryUtil.memGetFloat(v0) + org.lwjgl.system.MemoryUtil.memGetFloat(v2)) / 2.0F;
            float cy = (org.lwjgl.system.MemoryUtil.memGetFloat(v0 + 4) + org.lwjgl.system.MemoryUtil.memGetFloat(v2 + 4)) / 2.0F;
            float cz = (org.lwjgl.system.MemoryUtil.memGetFloat(v0 + 8) + org.lwjgl.system.MemoryUtil.memGetFloat(v2 + 8)) / 2.0F;
            d[q] = ORIGIN.distanceSquared(cx, cy, cz);
        }
        int[] order = memo(d, quads);
        if (CHECK) {
            int[] ref = new int[quads];
            for (int i = 0; i < quads; i++) ref[i] = i;
            float[] dd = java.util.Arrays.copyOf(d, quads);
            IntArrays.mergeSort(ref, (a, b) -> com.google.common.primitives.Floats.compare(dd[b], dd[a]));
            checked++;
            if (!java.util.Arrays.equals(ref, order)) mismatches++;
        }
        var type = ds.indexType();
        long p = bb.reserve(quads * 6 * type.bytes);
        if (type == com.mojang.blaze3d.vertex.VertexFormat.IndexType.SHORT) {
            for (int j : order) {
                int b = j * 4;
                org.lwjgl.system.MemoryUtil.memPutShort(p, (short) b);
                org.lwjgl.system.MemoryUtil.memPutShort(p + 2, (short) (b + 1));
                org.lwjgl.system.MemoryUtil.memPutShort(p + 4, (short) (b + 2));
                org.lwjgl.system.MemoryUtil.memPutShort(p + 6, (short) (b + 2));
                org.lwjgl.system.MemoryUtil.memPutShort(p + 8, (short) (b + 3));
                org.lwjgl.system.MemoryUtil.memPutShort(p + 10, (short) b);
                p += 12;
            }
        } else {
            for (int j : order) {
                int b = j * 4;
                org.lwjgl.system.MemoryUtil.memPutInt(p, b);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 4, b + 1);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 8, b + 2);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 12, b + 2);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 16, b + 3);
                org.lwjgl.system.MemoryUtil.memPutInt(p + 20, b);
                p += 24;
            }
        }
        ((dev.sleep.opt.MeshDataAccessor) (Object) m).sleep$setIndexBuffer(bb.build());
        fastSorts++;
        fastQuads += quads;
        return null;
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "quad sorts %d (%d quads), direct buffer sorts %d (%d quads, same keys as last time %d)%s", sorts, quads, fastSorts, fastQuads, memoHits,
                CHECK ? ", checked " + checked + ", mismatches " + mismatches : "");
    }
}
