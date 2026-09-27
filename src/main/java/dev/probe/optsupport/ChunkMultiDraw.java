package dev.probe.optsupport;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * exp/chunk-multidraw (client): the opaque chunk layers (solid, cutout_mipped, cutout) drawn with one
 * glMultiDrawElementsBaseVertex per region of 8×4×8 sections instead of one bind + uniform + draw per section.
 *
 * <p>Each section's vertices are also copied, unchanged, into its region's arena buffer (one per region and layer; a
 * first-fit free list; grown with glCopyBufferSubData). The per-section offset that vanilla uploads as the ChunkOffset
 * uniform before every draw is written, as the same float values, into a uniform block indexed by gl_DrawIDARB: the
 * patched vertex shader adds exactly the same vec3 to the same Position, so the output is bit-identical (the frame hash
 * checks it). Needs GL 4.6 or ARB_shader_draw_parameters and the vanilla chunk shader sources (a resource pack's own
 * shader is not patched: that layer keeps vanilla's path). Sections without an arena entry are drawn the vanilla way.
 */
public final class ChunkMultiDraw {
    /** draws per multi-draw call = offsets per uniform block (16 KB, the guaranteed minimum block size) */
    public static final int MAX_DRAWS = 1024;
    /** -Dlab.chunkmd.noFaceCull=true: every face bucket drawn (A/B of the direction culling) */
    private static final boolean FACE_CULL = !Boolean.getBoolean("lab.chunkmd.noFaceCull");
    private static final int REGION_W = 8, REGION_H = 4;
    private static final int UBO_BINDING = 7;
    /** vertex shader names patched by ChunkShaderPatchMixin */
    public static final Set<String> PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final Set<String> SUPPORTED = Set.of("rendertype_solid", "rendertype_cutout_mipped", "rendertype_cutout");
    public static long multiDraws, sectionsMulti, sectionsVanilla, sectionsSkipped, uploads, grows, quadsTotal, quadsDrawn;

    private static Boolean capable;

    private ChunkMultiDraw() {}

    /** GL 4.6 or ARB_shader_draw_parameters (render thread). */
    public static boolean capable() {
        Boolean c = capable;
        if (c == null) {
            GLCapabilities caps = GL.getCapabilities();
            capable = c = caps.OpenGL46 || caps.GL_ARB_shader_draw_parameters;
            System.out.println("[probe/opt] chunk multidraw: " + (c ? "available" : "not available (needs GL 4.6 or ARB_shader_draw_parameters)"));
        }
        return c;
    }

    /** Shader source patch: the vanilla chunk vertex shader, offset from the uniform block while multi-drawing. */
    public static String patch(String name, String src) {
        String decl = "uniform vec3 ChunkOffset;";
        String use = "vec3 pos = Position + ChunkOffset;";
        if (!SUPPORTED.contains(name) || !src.startsWith("#version 150") || !src.contains(decl) || !src.contains(use)) return src;
        String out = src.replaceFirst("#version 150", "#version 150\n#extension GL_ARB_shader_draw_parameters : require")
                .replace(decl, decl + "\nuniform int ProbeMultiDraw;\nlayout(std140) uniform ProbeChunkOffsets { vec4 ProbeOffsets[" + MAX_DRAWS + "]; };")
                .replace(use, "vec3 pos = Position + (ProbeMultiDraw != 0 ? ProbeOffsets[gl_DrawIDARB].xyz : ChunkOffset);");
        PATCHED.add(name);
        return out;
    }

    // ------------------------------------------------------------------ arenas

    /**
     * Face buckets, in the order they are stored: -X, -Y, -Z, other, +X, +Y, +Z (a camera in one octant sees the three
     * positive or negative ones plus "other", often one contiguous run). bq[k] = first quad of bucket k, bq[7] = quads.
     */
    static final class Entry {
        int start, vertices, indexCount;
        long origin;
        final int[] bq = new int[8];
        /** per bucket: the face plane nearest the camera side that could see it (+ buckets min, - buckets max coordinate, section-relative) */
        final float[] plane = new float[7];
    }

    private static ByteBuffer scratch = MemoryUtil.memAlloc(1 << 20);
    private static int[] quadBucket = new int[1 << 14];

    /**
     * The bucket of each quad from its geometric normal (both triangles of the quad, in the draw's winding): exactly
     * axis-aligned -> that direction, anything else -> "other". Then the quads copied bucket by bucket, stable (the
     * order inside a bucket is vanilla's: coplanar overlays keep their order).
     */
    private static ByteBuffer bucketed(ByteBuffer vb, int quads, int stride, int[] bq, float[] plane) {
        int bytes = quads * 4 * stride;
        if (scratch.capacity() < bytes) {
            MemoryUtil.memFree(scratch);
            scratch = MemoryUtil.memAlloc(Math.max(bytes, scratch.capacity() * 2));
        }
        if (quadBucket.length < quads) quadBucket = new int[Math.max(quads, quadBucket.length * 2)];
        long base = MemoryUtil.memAddress(vb);
        int[] count = new int[7];
        for (int k = 0; k < 3; k++) {
            plane[k] = Float.NEGATIVE_INFINITY;
            plane[k + 4] = Float.POSITIVE_INFINITY;
        }
        for (int q = 0; q < quads; q++) {
            long v = base + (long) q * 4 * stride;
            int b = 3;
            if (FACE_CULL) {
                int n1 = axisNormal(v, v + stride, v + 2L * stride), n2 = axisNormal(v + 2L * stride, v + 3L * stride, v);
                if (n1 >= 0 && n1 == n2) {
                    b = n1;
                    // model elements may reach outside the block and the section: the bucket's own extreme plane
                    float c = MemoryUtil.memGetFloat(v + 4L * (b & 3));
                    if (b < 3) plane[b] = Math.max(plane[b], c);
                    else plane[b] = Math.min(plane[b], c);
                }
            }
            quadBucket[q] = b;
            count[b]++;
        }
        int[] next = new int[7];
        for (int k = 0, acc = 0; k < 7; k++) {
            bq[k] = acc;
            next[k] = acc;
            acc += count[k];
        }
        bq[7] = quads;
        long dst = MemoryUtil.memAddress(scratch);
        int quadBytes = 4 * stride;
        for (int q = 0; q < quads; q++) {
            int b = quadBucket[q];
            MemoryUtil.memCopy(base + (long) q * quadBytes, dst + (long) next[b]++ * quadBytes, quadBytes);
        }
        scratch.limit(bytes).position(0);
        return scratch;
    }

    /** Bucket of the triangle a, b, c by its normal (b - a) x (c - b): 0 -X, 1 -Y, 2 -Z, 4 +X, 5 +Y, 6 +Z, -1 not axis-aligned. */
    private static int axisNormal(long a, long b, long c) {
        float ax = MemoryUtil.memGetFloat(a), ay = MemoryUtil.memGetFloat(a + 4), az = MemoryUtil.memGetFloat(a + 8);
        float bx = MemoryUtil.memGetFloat(b), by = MemoryUtil.memGetFloat(b + 4), bz = MemoryUtil.memGetFloat(b + 8);
        float cx = MemoryUtil.memGetFloat(c), cy = MemoryUtil.memGetFloat(c + 4), cz = MemoryUtil.memGetFloat(c + 8);
        float ux = bx - ax, uy = by - ay, uz = bz - az, wx = cx - bx, wy = cy - by, wz = cz - bz;
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        if (ny == 0 && nz == 0 && nx != 0) return nx > 0 ? 4 : 0;
        if (nx == 0 && nz == 0 && ny != 0) return ny > 0 ? 5 : 1;
        if (nx == 0 && ny == 0 && nz != 0) return nz > 0 ? 6 : 2;
        return -1;
    }

    static final class Arena {
        final long key;
        final RenderType layer;
        int vbo, vao, capacity, stride;   // capacity in vertices
        boolean vaoStale = true;
        final TreeMap<Integer, Integer> free = new TreeMap<>();   // start -> length (vertices)
        final IdentityHashMap<SectionRenderDispatcher.RenderSection, Entry> entries = new IdentityHashMap<>();
        // per-frame draw list
        int n, maxIndices;
        final long counts = MemoryUtil.nmemAlloc(4L * MAX_DRAWS), bases = MemoryUtil.nmemAlloc(4L * MAX_DRAWS), indices = MemoryUtil.nmemCalloc(MAX_DRAWS, 8);
        final ByteBuffer offsets = MemoryUtil.memAlloc(16 * MAX_DRAWS);
        boolean listed;

        Arena(long key, RenderType layer) {
            this.key = key;
            this.layer = layer;
        }

        int alloc(int vertices) {
            for (var e : free.entrySet()) {
                if (e.getValue() >= vertices) {
                    int s = e.getKey(), len = e.getValue();
                    free.remove(s);
                    if (len > vertices) free.put(s + vertices, len - vertices);
                    return s;
                }
            }
            return -1;
        }

        void release(int start, int vertices) {
            // merge with neighbours
            Integer prev = free.floorKey(start - 1);
            if (prev != null && prev + free.get(prev) == start) {
                start = prev;
                vertices += free.remove(prev);
            }
            Integer next = free.get(start + vertices);
            if (next != null) {
                free.remove(start + vertices);
                vertices += next;
            }
            free.put(start, vertices);
        }

        void grow(int needed) {
            int cap = Math.max(capacity * 2, capacity + needed + 4096);
            int nb = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, nb);
            GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, (long) cap * stride, GL15C.GL_STATIC_DRAW);
            if (vbo != 0) {
                GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, vbo);
                GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, 0, 0, (long) capacity * stride);
                GL15C.glDeleteBuffers(vbo);
            }
            release(capacity, cap - capacity);
            vbo = nb;
            capacity = cap;
            vaoStale = true;
            grows++;
        }
    }

    private static final Long2ObjectOpenHashMap<Arena>[] ARENAS = newMaps();
    private static final List<RenderType> LAYERS = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static Long2ObjectOpenHashMap<Arena>[] newMaps() {
        Long2ObjectOpenHashMap<Arena>[] m = new Long2ObjectOpenHashMap[8];
        for (int i = 0; i < m.length; i++) m[i] = new Long2ObjectOpenHashMap<>();
        return m;
    }

    private static int layerIndex(RenderType t) {
        int i = LAYERS.indexOf(t);
        if (i < 0 && LAYERS.size() < 8) {
            LAYERS.add(t);
            i = LAYERS.size() - 1;
        }
        return i;
    }

    public static boolean layerSupported(RenderType t) {
        return t == RenderType.solid() || t == RenderType.cutoutMipped() || t == RenderType.cutout();
    }

    private static long regionKey(long sx, long sy, long sz) {
        return ((sx >> 3) & 0x1FFFFF) | (((sy >> 2) & 0x3FFFFF) << 21) | (((sz >> 3) & 0x1FFFFF) << 43);
    }

    /** From VertexBuffer.upload of a section's layer buffer, before vanilla consumes the mesh (render thread). */
    public static boolean store(SectionRenderDispatcher.RenderSection section, RenderType layer, MeshData mesh) {
        MeshData.DrawState ds = mesh.drawState();
        if (!layerSupported(layer) || mesh.indexBuffer() != null || ds.mode() != VertexFormat.Mode.QUADS || !capable()) return false;
        release(section, layer);
        int li = layerIndex(layer);
        if (li < 0) return false;
        var o = section.getOrigin();
        long key = regionKey(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4);
        Arena a = ARENAS[li].get(key);
        if (a == null) ARENAS[li].put(key, a = new Arena(key, layer));
        int stride = ds.format().getVertexSize();
        if (a.stride == 0) a.stride = stride;
        if (a.stride != stride) return false;   // another format: vanilla path
        int verts = ds.vertexCount();
        int start = a.alloc(verts);
        if (start < 0) {
            a.grow(verts);
            start = a.alloc(verts);
        }
        Entry e = new Entry();
        ByteBuffer vb = mesh.vertexBuffer();
        if (ds.format().getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION) == 0 && verts % 4 == 0) {
            vb = bucketed(vb, verts / 4, stride, e.bq, e.plane);
        } else {
            java.util.Arrays.fill(e.bq, 0, 4, 0);   // everything in "other"
            for (int k = 4; k < 8; k++) e.bq[k] = verts / 4;
        }
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, a.vbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) start * stride, vb);
        e.start = start;
        e.vertices = verts;
        e.indexCount = ds.indexCount();
        e.origin = o.asLong();
        a.entries.put(section, e);
        uploads++;
        return true;
    }

    /** The vanilla buffer is not filled for arena layers (no duplicate VRAM) while the layer's shader is patched. */
    public static boolean replacesVanilla(RenderType layer) {
        return !KEEP_VANILLA && PATCHED.size() == SUPPORTED.size();
    }

    private static final boolean KEEP_VANILLA = Boolean.getBoolean("lab.chunkmd.keepVanilla");

    /** From RenderSection.reset (origin change, release): every layer's entry. */
    public static void release(SectionRenderDispatcher.RenderSection section) {
        for (RenderType t : LAYERS) release(section, t);
    }

    private static void release(SectionRenderDispatcher.RenderSection section, RenderType layer) {
        int li = LAYERS.indexOf(layer);
        if (li < 0) return;
        for (Arena a : ARENAS[li].values()) {
            Entry e = a.entries.remove(section);
            if (e != null) {
                a.release(e.start, e.vertices);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private static final List<Arena> LISTED = new ArrayList<>();
    private static final Map<Integer, int[]> PROGRAM_STATE = new java.util.HashMap<>();   // program -> {flagLocation}

    /**
     * Draws the layer's visible sections: arena sections queued per region, others (no entry, stale origin) the vanilla
     * way on the spot. Returns false when the layer or the current shader cannot use it (then the caller draws vanilla).
     */
    public static boolean draw(RenderType layer, Iterable<SectionRenderDispatcher.RenderSection> visible, double x, double y, double z,
            net.minecraft.client.renderer.ShaderInstance shader) {
        int li = LAYERS.indexOf(layer);
        if (li < 0) return false;
        int[] ps = programState(shader.getId());
        if (ps == null) return false;
        var uniform = shader.CHUNK_OFFSET;
        Long2ObjectOpenHashMap<Arena> arenas = ARENAS[li];
        LISTED.clear();
        for (SectionRenderDispatcher.RenderSection s : visible) {
            if (s.getCompiled().isEmpty(layer)) continue;
            var o = s.getOrigin();
            Arena a = arenas.get(regionKey(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4));
            Entry e = a == null ? null : a.entries.get(s);
            float ox = (float) ((double) o.getX() - x), oy = (float) ((double) o.getY() - y), oz = (float) ((double) o.getZ() - z);
            if (e == null || e.origin != o.asLong()) {
                if (replacesVanilla(layer)) {   // its vanilla buffer holds nothing current: nothing to draw
                    sectionsSkipped++;
                    continue;
                }
                // vanilla, now
                GL20C.glUniform1i(ps[0], 0);
                if (uniform != null) {
                    uniform.set(ox, oy, oz);
                    uniform.upload();
                }
                var vb = s.getBuffer(layer);
                vb.bind();
                vb.draw();
                sectionsVanilla++;
                continue;
            }
            if (!a.listed) {
                a.listed = true;
                a.n = 0;
                a.maxIndices = 0;
                LISTED.add(a);
            }
            if (a.n + 4 > MAX_DRAWS) flush(a, layer, ps);
            // visible face buckets: a face whose normal points away from the camera (or edge-on) would be culled by the
            // GPU anyway; every +X face of the section lies at x >= minX, so with the camera at x <= minX none is visible
            // (a margin of 1/64 block keeps it conservative against float rounding: more faces drawn, never fewer)
            float[] pl = e.plane;
            double rx = x - o.getX(), ry = y - o.getY(), rz = z - o.getZ();
            int vis = 1 << 3;   // other
            if (rx < pl[0] + EPS) vis |= 1;
            if (ry < pl[1] + EPS) vis |= 1 << 1;
            if (rz < pl[2] + EPS) vis |= 1 << 2;
            if (rx > pl[4] - EPS) vis |= 1 << 4;
            if (ry > pl[5] - EPS) vis |= 1 << 5;
            if (rz > pl[6] - EPS) vis |= 1 << 6;
            int[] bq = e.bq;
            int runStart = -1, runEnd = -1;
            for (int k = 0; k <= 7; k++) {
                boolean take = k < 7 && (vis & (1 << k)) != 0;
                boolean empty = k < 7 && bq[k + 1] == bq[k];
                if (take && !empty) {
                    if (runStart < 0) runStart = bq[k];
                    runEnd = bq[k + 1];
                } else if (!empty && runStart >= 0) {   // an invisible, non-empty bucket (or the end) closes the run
                    addDraw(a, e.start + runStart * 4, (runEnd - runStart) * 6, ox, oy, oz);
                    quadsDrawn += runEnd - runStart;
                    runStart = -1;
                }
            }
            quadsTotal += bq[7];
            sectionsMulti++;
        }
        for (Arena a : LISTED) {
            a.listed = false;
            flush(a, layer, ps);
        }
        return true;
    }

    private static final double EPS = 1.0 / 64;

    private static void addDraw(Arena a, int baseVertex, int indices, float ox, float oy, float oz) {
        int i = a.n++;
        MemoryUtil.memPutInt(a.counts + 4L * i, indices);
        MemoryUtil.memPutInt(a.bases + 4L * i, baseVertex);
        a.offsets.putFloat(16 * i, ox).putFloat(16 * i + 4, oy).putFloat(16 * i + 8, oz);
        if (indices > a.maxIndices) a.maxIndices = indices;
    }

    /** Issues the arena's queued draws as one multi-draw. */
    private static void flush(Arena a, RenderType layer, int[] ps) {
        if (a.n == 0) return;
        GL20C.glUniform1i(ps[0], 1);
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        if (a.vaoStale || a.vao == 0) setupVao(a, layer);
        GL30C.glBindVertexArray(a.vao);
        seq.bind(a.maxIndices);   // element buffer of this VAO, large enough
        GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ubo());
        a.offsets.limit(16 * a.n).position(0);
        GL15C.glBufferSubData(GL31C.GL_UNIFORM_BUFFER, 0, a.offsets);
        a.offsets.clear();
        GL31C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, UBO_BINDING, ubo());
        GL32C.nglMultiDrawElementsBaseVertex(GL15C.GL_TRIANGLES, a.counts, seq.type().asGLType, a.indices, a.n, a.bases);
        GL20C.glUniform1i(ps[0], 0);
        multiDraws++;
        a.n = 0;
        a.maxIndices = 0;
    }

    private static int ubo;

    private static int ubo() {
        if (ubo == 0) {
            ubo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ubo);
            GL15C.glBufferData(GL31C.GL_UNIFORM_BUFFER, 16L * MAX_DRAWS, GL15C.GL_DYNAMIC_DRAW);
        }
        return ubo;
    }

    /** The patched program's flag uniform and block binding, looked up once; null if the program is not patched. */
    private static int[] programState(int program) {
        int[] s = PROGRAM_STATE.get(program);
        if (s == null) {
            int flag = GL20C.glGetUniformLocation(program, "ProbeMultiDraw");
            int block = GL31C.glGetUniformBlockIndex(program, "ProbeChunkOffsets");
            if (flag < 0 || block == GL31C.GL_INVALID_INDEX) {
                s = new int[0];
            } else {
                GL31C.glUniformBlockBinding(program, block, UBO_BINDING);
                s = new int[] {flag};
            }
            PROGRAM_STATE.put(program, s);
        }
        return s.length == 0 ? null : s;
    }

    private static void setupVao(Arena a, RenderType layer) {
        if (a.vao == 0) a.vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(a.vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, a.vbo);
        layer.format().setupBufferState();
        a.vaoStale = false;
    }

    public static String report() {
        int arenas = 0;
        long verts = 0;
        for (var m : ARENAS) {
            arenas += m.size();
            for (Arena a : m.values()) verts += a.capacity;
        }
        return String.format(java.util.Locale.ROOT, "chunk multidraw: patched %s, multi-draw calls %d, sections by multi-draw %d, by vanilla draws %d, skipped %d, quads drawn %.1f %% (face culling %s), uploads %d, arenas %d (%.1f MB), grows %d, vanilla buffers %s",
                PATCHED, multiDraws, sectionsMulti, sectionsVanilla, sectionsSkipped, 100.0 * quadsDrawn / Math.max(1, quadsTotal), FACE_CULL ? "on" : "off",
                uploads, arenas, verts * 32 / 1048576.0, grows,
                KEEP_VANILLA || PATCHED.size() != SUPPORTED.size() ? "filled" : "not filled");
    }
}
