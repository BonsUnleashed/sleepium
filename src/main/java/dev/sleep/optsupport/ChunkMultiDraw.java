package dev.sleep.optsupport;

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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL43C;
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
    /** exp/shader-light: programs patched to take light from recipes and volumes */
    public static final Set<String> LIGHT_PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
            System.out.println("[sleep/opt] chunk multidraw: " + (c ? "available" : "not available (needs GL 4.6 or ARB_shader_draw_parameters)"));
        }
        return c;
    }

    /** Shader source patch: the vanilla chunk vertex shader, offset from the uniform block while multi-drawing. */
    public static String patch(String name, String src) {
        if ("rendertype_translucent".equals(name)) return LightVolumes.patchTranslucent(src);
        String decl = "uniform vec3 ChunkOffset;";
        String use = "vec3 pos = Position + ChunkOffset;";
        String uv = "texCoord0 = UV0;";
        if (!SUPPORTED.contains(name) || !src.startsWith("#version 150") || !src.contains(decl) || !src.contains(use) || !src.contains(uv)) return src;
        // packed (mode 2): integers as floats, minus a whole number, times a power of two: the stored floats exactly.
        // No 'readonly' on storage blocks: a memory qualifier needs GLSL 4.20 (Intel's compiler refuses it under 150)
        String out = src.replaceFirst("#version 150", "#version 150\n#extension GL_ARB_shader_draw_parameters : require"
                        + (ssbo() ? "\n#extension GL_ARB_shader_storage_buffer_object : require" : ""))
                .replace(decl, decl + "\nuniform int ProbeMultiDraw;\n" + (ssbo()
                        ? "layout(std430) buffer ProbeChunkOffsets { vec4 ProbeOffsets[]; };"
                        : "layout(std140) uniform ProbeChunkOffsets { vec4 ProbeOffsets[" + MAX_DRAWS + "]; };"))
                .replace(use, "vec3 pos = Position; if (ProbeMultiDraw == 2) pos = (Position - 2048.0) * 0.00390625; if (ProbeMultiDraw != 0) pos += ProbeOffsets[gl_DrawIDARB].xyz; else pos += ChunkOffset;")
                // (statements, no ternary inside an expression: Veil reprints vanilla shader sources and drops their parentheses)
                .replace(uv, "texCoord0 = ProbeMultiDraw == 2 ? UV0 * 0.0000152587890625 : UV0;");
        String light = "vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);";
        if (LightVolumes.ON && ssbo() && out.contains(light)) {
            out = out.replaceFirst("#extension GL_ARB_shader_draw_parameters : require", "#extension GL_ARB_shader_draw_parameters : require\n#extension GL_ARB_explicit_attrib_location : require")
                    .replace("void main() {", LightVolumes.glsl() + "\nvoid main() {")
                    .replace(light, "ivec2 probe_uv2 = ProbeMultiDraw != 0 ? probeUV2(ProbeOffsets[gl_DrawIDARB].w) : UV2; vertexColor = Color * minecraft_sample_lightmap(Sampler2, probe_uv2);");
            LIGHT_PATCHED.add(name);
        }
        PATCHED.add(name);
        return out;
    }

    // ------------------------------------------------------------------ arenas

    /**
     * Face buckets, in the order they are stored: -X, -Y, -Z, other, +X, +Y, +Z (a camera in one octant sees the three
     * positive or negative ones plus "other", often one contiguous run). bq[k] = first quad of bucket k, bq[7] = quads.
     */
    static final class Entry {
        Arena arena;
        int start, vertices, indexCount;
        /** exp/shader-light: the mesh has light recipes; the section's light volume (-1: none, baked light) */
        boolean recipes;
        int vol = -1;
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
        // per-frame draw list
        int n, maxIndices, drawCap = MAX_DRAWS;
        long counts = MemoryUtil.nmemAlloc(4L * MAX_DRAWS), bases = MemoryUtil.nmemAlloc(4L * MAX_DRAWS), indices = MemoryUtil.nmemCalloc(MAX_DRAWS, 8);
        ByteBuffer offsets = MemoryUtil.memAlloc(16 * MAX_DRAWS);

        /** Storage-buffer offsets have no size limit: the draw list grows instead of being split. */
        void ensureDraws(int needed) {
            if (needed <= drawCap) return;
            int c = Math.max(needed, drawCap * 2);
            counts = MemoryUtil.nmemRealloc(counts, 4L * c);
            bases = MemoryUtil.nmemRealloc(bases, 4L * c);
            MemoryUtil.nmemFree(indices);
            indices = MemoryUtil.nmemCalloc(c, 8);
            ByteBuffer o = MemoryUtil.memAlloc(16 * c);
            MemoryUtil.memCopy(MemoryUtil.memAddress(offsets), MemoryUtil.memAddress(o), 16L * n);
            MemoryUtil.memFree(offsets);
            offsets = o;
            drawCap = c;
        }
        boolean listed;
        final boolean packed;
        int uboOffset;
        long cmdOffset;

        Arena(long key, RenderType layer, boolean packed) {
            this.key = key;
            this.layer = layer;
            this.packed = packed;
            if (packed) stride = PACKED_STRIDE;
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

    private static final Long2ObjectOpenHashMap<Arena>[] ARENAS = newMaps(), PACKED_ARENAS = newMaps();
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

    /**
     * The full-format arena of a section: with packing on, only the ~3 % of quads that do not pack exactly go there, so
     * their regions are 4x wider in x and z (32x4x32 sections) and there are few extra multi-draws.
     */
    /**
     * The packed arena of a section: -Dlab.chunkmd.bigRegions (default on) makes them 32x8x32 sections, so a layer has a
     * handful of arenas (each a VAO bind, index bind, offsets range and one indirect call) instead of dozens; a call
     * over 1024 draws is split as before. Off: 8x4x8.
     */
    private static long packedKey(long sx, long sy, long sz) {
        if (SINGLE && ssbo()) return 0;
        return BIG_REGIONS ? regionKey(sx >> 2, sy >> 1, sz >> 2) : regionKey(sx, sy, sz);
    }

    private static final boolean BIG_REGIONS = !"false".equals(System.getProperty("lab.chunkmd.bigRegions"));

    /** -Dlab.chunkmd.single (default on, with storage buffers): one arena per layer and format, one call each. */
    private static final boolean SINGLE = !"false".equals(System.getProperty("lab.chunkmd.single"));

    private static long fullKey(long sx, long sy, long sz) {
        if (SINGLE && ssbo()) return 0;
        return PACK ? regionKey(sx >> 2, BIG_REGIONS ? sy >> 1 : sy, sz >> 2) : regionKey(sx, sy, sz);
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
        long key = packedKey(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4), fk = fullKey(o.getX() >> 4, o.getY() >> 4, o.getZ() >> 4);
        Arena a = ARENAS[li].get(fk);
        if (a == null) ARENAS[li].put(fk, a = new Arena(fk, layer, false));
        int stride = ds.format().getVertexSize();
        if (a.stride == 0) a.stride = stride;
        if (a.stride != stride) return false;   // another format: vanilla path
        int verts = ds.vertexCount();
        Entry e = new Entry();
        ByteBuffer vb = mesh.vertexBuffer();
        if (PACK_STATS && stride == 32) packStats(vb, verts);
        if (ds.format().getOffset(com.mojang.blaze3d.vertex.VertexFormatElement.POSITION) == 0 && verts % 4 == 0) {
            vb = bucketed(vb, verts / 4, stride, e.bq, e.plane);
        } else {
            java.util.Arrays.fill(e.bq, 0, 4, 0);   // everything in "other"
            for (int k = 4; k < 8; k++) e.bq[k] = verts / 4;
        }
        e.origin = o.asLong();
        e.recipes = LightVolumes.hasRecipes(mesh);
        uploads++;
        // meshes with light recipes (exp/shader-light) keep the 32-byte format: the packed one has no room for them
        if (PACK && ds.format() == DefaultVertexFormat.BLOCK && verts % 4 == 0 && !LightVolumes.hasRecipes(mesh)) {
            Entry pe = new Entry();
            pe.origin = e.origin;
            System.arraycopy(e.plane, 0, pe.plane, 0, 7);
            ByteBuffer full = split(vb, verts / 4, e.bq, pe.bq);   // packed quads into PACKED_SCRATCH, the rest returned
            int packedQuads = pe.bq[7], fullQuads = e.bq[7];
            if (packedQuads > 0) {
                Arena pa = PACKED_ARENAS[li].get(key);
                if (pa == null) PACKED_ARENAS[li].put(key, pa = new Arena(key, layer, true));
                put(pa, pe, packedScratch, packedQuads * 4, section);
            }
            if (fullQuads > 0) put(a, e, full, fullQuads * 4, section);
            quadsPacked += packedQuads;
            quadsFull += fullQuads;
            return true;
        }
        put(a, e, vb, verts, section);
        return true;
    }

    private static void put(Arena a, Entry e, ByteBuffer data, int verts, SectionRenderDispatcher.RenderSection section) {
        int start = a.alloc(verts);
        if (start < 0) {
            a.grow(verts);
            start = a.alloc(verts);
        }
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, a.vbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) start * a.stride, data);
        e.start = start;
        e.vertices = verts;
        e.indexCount = verts / 4 * 6;
        e.arena = a;
        ((ChunkEntries) section).sleep$md()[slot(a)] = e;
        SectionLists.changed();
    }

    // ------------------------------------------------------------------ packed vertices (16 bytes, bit-exact)

    /** -Dlab.chunkmd.noPack=true: every quad in the 32-byte vanilla format */
    private static final boolean PACK = !Boolean.getBoolean("lab.chunkmd.noPack");
    static final int PACKED_STRIDE = 16;
    public static long quadsPacked, quadsFull;
    private static ByteBuffer packedScratch = MemoryUtil.memAlloc(1 << 19), fullScratch = MemoryUtil.memAlloc(1 << 20);

    /**
     * Splits bucketed BLOCK quads (32 bytes per vertex) into packed ones (written to packedScratch, 16 bytes per vertex)
     * and the rest (returned, still 32 bytes), each keeping bucket order; fb / pb receive the bucket starts of each part.
     * A quad is packed only if every vertex survives exactly: position (x + 8) * 256 an integer in 0..65535, UV * 65536
     * an integer in 0..65535 (no -0.0), both light shorts <= 255. Layout: position u16x3 @0, light u8x2 @6,
     * colour rgba8 @8 (as stored), UV u16x2 @12. The Normal attribute is not read by the chunk shaders.
     */
    private static ByteBuffer split(ByteBuffer vb, int quads, int[] fb, int[] pb) {
        if (packedScratch.capacity() < quads * 64) {
            MemoryUtil.memFree(packedScratch);
            packedScratch = MemoryUtil.memAlloc(Math.max(quads * 64, packedScratch.capacity() * 2));
        }
        if (fullScratch.capacity() < quads * 128) {
            MemoryUtil.memFree(fullScratch);
            fullScratch = MemoryUtil.memAlloc(Math.max(quads * 128, fullScratch.capacity() * 2));
        }
        long src = MemoryUtil.memAddress(vb), pdst = MemoryUtil.memAddress(packedScratch), fdst = MemoryUtil.memAddress(fullScratch);
        int[] bucketStart = fb.clone();
        int np = 0, nf = 0;
        for (int k = 0; k < 7; k++) {
            pb[k] = np;
            fb[k] = nf;
            for (int q = bucketStart[k]; q < bucketStart[k + 1]; q++) {
                long v = src + 128L * q;
                if (packable(v) && packable(v + 32) && packable(v + 64) && packable(v + 96)) {
                    long d = pdst + 64L * np;
                    for (int i = 0; i < 4; i++) pack(v + 32L * i, d + 16L * i);
                    np++;
                } else {
                    MemoryUtil.memCopy(v, fdst + 128L * nf, 128);
                    nf++;
                }
            }
        }
        pb[7] = np;
        fb[7] = nf;
        packedScratch.limit(np * 64).position(0);
        fullScratch.limit(nf * 128).position(0);
        return fullScratch;
    }

    private static boolean exact(float f, double bias, double scale) {
        if (Float.floatToRawIntBits(f) == 0x80000000) return false;
        double d = ((double) f + bias) * scale;
        return d >= 0 && d <= 65535 && d == Math.rint(d);
    }

    private static boolean packable(long v) {
        int l = MemoryUtil.memGetInt(v + 24);
        return exact(MemoryUtil.memGetFloat(v), 8, 256) && exact(MemoryUtil.memGetFloat(v + 4), 8, 256) && exact(MemoryUtil.memGetFloat(v + 8), 8, 256)
                && exact(MemoryUtil.memGetFloat(v + 16), 0, 65536) && exact(MemoryUtil.memGetFloat(v + 20), 0, 65536)
                && (l & 0xFFFF) <= 255 && (l >>> 16) <= 255;
    }

    private static void pack(long v, long d) {
        for (int i = 0; i < 3; i++) MemoryUtil.memPutShort(d + 2L * i, (short) (int) (((double) MemoryUtil.memGetFloat(v + 4L * i) + 8) * 256));
        int l = MemoryUtil.memGetInt(v + 24);
        MemoryUtil.memPutByte(d + 6, (byte) (l & 0xFF));
        MemoryUtil.memPutByte(d + 7, (byte) ((l >>> 16) & 0xFF));
        MemoryUtil.memPutInt(d + 8, MemoryUtil.memGetInt(v + 12));
        MemoryUtil.memPutShort(d + 12, (short) (int) ((double) MemoryUtil.memGetFloat(v + 16) * 65536));
        MemoryUtil.memPutShort(d + 14, (short) (int) ((double) MemoryUtil.memGetFloat(v + 20) * 65536));
    }

    /** The vanilla buffer is not filled for arena layers (no duplicate VRAM) while the layer's shader is patched. */
    public static boolean replacesVanilla(RenderType layer) {
        return ACTIVE && !KEEP_VANILLA && PATCHED.size() == SUPPORTED.size();
    }

    /**
     * Multi-draw replaces renderSectionLayer's body, so it gives way when another mod's mixin changes that method
     * (Sable / Aeronautics draw ships there, Veil hooks it): then the layers, and the uploads, stay vanilla's.
     */
    public static final boolean ACTIVE = !MixinTouch.touched("net/minecraft/client/renderer/LevelRenderer", "renderSectionLayer");

    private static final boolean KEEP_VANILLA = Boolean.getBoolean("lab.chunkmd.keepVanilla");

    /** From RenderSection.reset (origin change, release): every layer's entry. */
    public static void release(SectionRenderDispatcher.RenderSection section) {
        for (RenderType t : LAYERS) release(section, t);
        if (LightVolumes.ON) LightVolumes.released(section);
    }

    /**
     * RenderSection.setCompiled: a layer the new compile does not have is gone — vanilla stops drawing it
     * (CompiledSection.isEmpty) and no upload replaces its entry, so the entry is released here, at the same moment.
     */
    public static void compiled(SectionRenderDispatcher.RenderSection section, SectionRenderDispatcher.CompiledSection compiled) {
        // setCompiled runs on the compile task's thread: the arenas belong to the render thread, which drains this
        PENDING_COMPILED.add(new Object[] {section, compiled});
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> PENDING_COMPILED = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Render thread, before drawing: the entries of layers the sections' current compiles no longer have. */
    private static void drainCompiled() {
        Object[] p;
        while ((p = PENDING_COMPILED.poll()) != null) {
            var section = (SectionRenderDispatcher.RenderSection) p[0];
            var compiled = (SectionRenderDispatcher.CompiledSection) p[1];
            if (section.getCompiled() != compiled) continue;   // a newer compile (or a reset) came since
            for (RenderType t : LAYERS) if (compiled.isEmpty(t)) release(section, t);
        }
    }

    private static void release(SectionRenderDispatcher.RenderSection section, RenderType layer) {
        int li = LAYERS.indexOf(layer);
        if (li < 0) return;
        Object[] md = ((ChunkEntries) section).sleep$md();
        for (int k = 2 * li; k < 2 * li + 2; k++) {
            Entry e = (Entry) md[k];
            if (e != null) {
                e.arena.release(e.start, e.vertices);
                md[k] = null;
                SectionLists.changed();
            }
        }
    }

    /** A section's entry slot: layer index * 2, + 1 for the packed format. */
    private static int slot(Arena a) {
        return 2 * LAYERS.indexOf(a.layer) + (a.packed ? 1 : 0);
    }

    /** RenderSection (ChunkSectionMixin): its arena entries by slot, so drawing needs no lookup. */
    public interface ChunkEntries {
        Object[] sleep$md();

        /** SectionLists: the section's index in the visible list of build b (-1: not visible in it). */
        int sleep$visIdx(int b);

        void sleep$setVis(int idx, int b);

        /** SectionLists: the build whose dirty list holds the section (no duplicates). */
        int sleep$dirtyBuild();

        void sleep$setDirtyBuild(int b);

        /** Bumped by every setDirty and reset (blocks or light of the section changed, or it moved): KineticResident. */
        int sleep$epoch();
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
        if (dev.sleep.DrawStats.ON) dev.sleep.DrawStats.phaseBegin(4);
        try {
            return draw0(layer, visible, x, y, z, shader);
        } finally {
            if (dev.sleep.DrawStats.ON) dev.sleep.DrawStats.phaseEnd(4);
        }
    }

    private static boolean draw0(RenderType layer, Iterable<SectionRenderDispatcher.RenderSection> visible, double x, double y, double z,
            net.minecraft.client.renderer.ShaderInstance shader) {
        int li = LAYERS.indexOf(layer);
        if (li < 0) return false;
        if (!PENDING_COMPILED.isEmpty()) drainCompiled();
        int[] ps = programState(shader.getId());
        if (ps == null) return false;
        var uniform = shader.CHUNK_OFFSET;
        LISTED.clear();
        boolean replaces = replacesVanilla(layer);
        int fs = 2 * li;
        // with the vanilla buffers empty, only the sections with an entry matter: SectionLists keeps them, in order
        if (replaces && SECTION_LISTS && visible instanceof it.unimi.dsi.fastutil.objects.ObjectArrayList<SectionRenderDispatcher.RenderSection> vis)
            visible = SectionLists.layer(vis, li);
        // the same camera position over the same lists and contents gives the same draws: last frame's are drawn again
        // (offsets and face buckets depend on the position only, so turning the head keeps them)
        boolean reusable = REUSE && replaces && SECTION_LISTS && SINGLE && ssbo();
        LayerCache lc = LCACHE[li];
        long ver = SectionLists.version();
        if (reusable && lc.ver == ver && lc.x == x && lc.y == y && lc.z == z) {
            LISTED.addAll(lc.arenas);
            flushAll(layer, ps, true);
            drawReuses++;
            return true;
        }
        for (SectionRenderDispatcher.RenderSection s : visible) {
            // the section's own entry slots: no emptiness test (an empty layer has no entry), no map lookups
            Object[] md = ((ChunkEntries) s).sleep$md();
            Entry e = (Entry) md[fs], pe = (Entry) md[fs + 1];
            if (e == null && pe == null && (replaces || s.getCompiled().isEmpty(layer))) {
                if (replaces) sectionsSkipped++;
                continue;
            }
            var o = s.getOrigin();
            long origin = o.asLong();
            if (e != null && e.origin != origin) e = null;
            if (pe != null && pe.origin != origin) pe = null;
            Arena a = e == null ? null : e.arena, pa = pe == null ? null : pe.arena;
            float ox = (float) ((double) o.getX() - x), oy = (float) ((double) o.getY() - y), oz = (float) ((double) o.getZ() - z);
            if (e == null && pe == null) {
                if (replaces) {   // its vanilla buffer holds nothing current: nothing to draw
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
            if (pe != null) queue(pa, pe, o, x, y, z, ox, oy, oz, layer, ps);
            if (e != null) queue(a, e, o, x, y, z, ox, oy, oz, layer, ps);
            sectionsMulti++;
        }
        if (reusable) {
            lc.arenas.clear();
            lc.arenas.addAll(LISTED);
            lc.ver = ver;
            lc.x = x;
            lc.y = y;
            lc.z = z;
        } else {
            lc.ver = -1;
        }
        flushAll(layer, ps, reusable);
        return true;
    }

    /** -Dlab.chunkmd.reuse=false: build the draws every frame. */
    private static final boolean REUSE = !"false".equals(System.getProperty("lab.chunkmd.reuse"));
    public static long drawReuses;

    /** A layer's last draws (single arenas keep their lists when kept) and what they were built from. */
    static final class LayerCache {
        long ver = -1;
        double x = Double.NaN, y, z;
        final List<Arena> arenas = new ArrayList<>();
    }

    private static final LayerCache[] LCACHE = newCaches();

    private static LayerCache[] newCaches() {
        LayerCache[] c = new LayerCache[8];
        for (int i = 0; i < c.length; i++) c[i] = new LayerCache();
        return c;
    }

    private static ByteBuffer staging = MemoryUtil.memAlloc(1 << 16);
    private static int uboAlign;

    /**
     * The layer's queued draws: every arena's offsets in one orphaning upload (each arena then reads its own range),
     * packed arenas first and full-format ones after, so the mode uniform is set at most twice per layer.
     */
    private static void flushAll(RenderType layer, int[] ps, boolean keep) {
        boolean sb = ssbo();
        int target = sb ? GL43C.GL_SHADER_STORAGE_BUFFER : GL31C.GL_UNIFORM_BUFFER;
        if (uboAlign == 0) uboAlign = Math.max(16, GL11C.glGetInteger(sb ? GL43C.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT : GL31C.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT));
        int total = 0;
        for (Arena a : LISTED) {
            a.listed = false;
            if (a.n == 0) continue;
            a.uboOffset = total;
            total += (16 * a.n + uboAlign - 1) / uboAlign * uboAlign;
        }
        if (total == 0) return;
        if (staging.capacity() < total) {
            MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAlloc(Math.max(total, staging.capacity() * 2));
        }
        long st = MemoryUtil.memAddress(staging);
        for (Arena a : LISTED) if (a.n > 0) MemoryUtil.memCopy(MemoryUtil.memAddress(a.offsets), st + a.uboOffset, 16L * a.n);
        int buf = sb ? ssboBuffer() : ubo();
        GL15C.glBindBuffer(target, buf);
        // new storage each time (no wait on the previous frame's draws); a uniform block's full size past the last range
        GL15C.glBufferData(target, total + (sb ? 16L : 16L * MAX_DRAWS), GL15C.GL_STREAM_DRAW);
        staging.limit(total).position(0);
        GL15C.glBufferSubData(target, 0, staging);
        staging.clear();
        boolean indirect = indirect();
        long cmdBase = 0;
        if (indirect) {
            // every arena's commands {count, 1 instance, first index 0, base vertex, base instance 0} in one upload
            int cmds = 0;
            for (Arena a : LISTED) {
                if (a.n == 0) continue;
                a.cmdOffset = cmds * 20L;
                cmds += a.n;
            }
            long bytes = cmds * 20L;
            if (cmdStaging.capacity() < bytes) {
                MemoryUtil.memFree(cmdStaging);
                cmdStaging = MemoryUtil.memAlloc((int) Math.max(bytes, cmdStaging.capacity() * 2L));
            }
            long c = MemoryUtil.memAddress(cmdStaging);
            for (Arena a : LISTED) {
                if (a.n == 0) continue;
                long p = c + a.cmdOffset;
                for (int i = 0; i < a.n; i++, p += 20) {
                    MemoryUtil.memPutInt(p, MemoryUtil.memGetInt(a.counts + 4L * i));
                    MemoryUtil.memPutInt(p + 4, 1);
                    MemoryUtil.memPutInt(p + 8, 0);
                    MemoryUtil.memPutInt(p + 12, MemoryUtil.memGetInt(a.bases + 4L * i));
                    MemoryUtil.memPutInt(p + 16, 0);
                }
            }
            cmdBase = COMMANDS.upload(c, bytes);
        }
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        if (LightVolumes.ON && sb) GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, LightVolumes.BINDING, LightVolumes.buffer());
        int mode = -1;
        for (int pass = 0; pass < 2; pass++) {
            for (Arena a : LISTED) {
                if (a.n == 0 || a.packed != (pass == 0)) continue;
                int m = a.packed ? 2 : 1;
                if (m != mode) GL20C.glUniform1i(ps[0], mode = m);
                if (a.vaoStale || a.vao == 0) setupVao(a, layer);
                GL30C.glBindVertexArray(a.vao);
                seq.bind(a.maxIndices);
                GL30C.glBindBufferRange(target, UBO_BINDING, buf, a.uboOffset, sb ? 16L * a.n : 16L * MAX_DRAWS);
                if (dev.sleep.DrawStats.ON) dev.sleep.DrawStats.phaseBegin(3);
                if (indirect) {
                    GL43C.glMultiDrawElementsIndirect(GL15C.GL_TRIANGLES, seq.type().asGLType, cmdBase + a.cmdOffset, a.n, 20);
                    if (IndirectDiag.ON) IndirectDiag.after("chunks", seq.type().asGLType, cmdBase + a.cmdOffset, a.n, 20);
                } else GL32C.nglMultiDrawElementsBaseVertex(GL15C.GL_TRIANGLES, a.counts, seq.type().asGLType, a.indices, a.n, a.bases);
                if (dev.sleep.DrawStats.ON) dev.sleep.DrawStats.phaseEnd(3);
                multiDraws++;
                if (!keep) {
                    a.n = 0;
                    a.maxIndices = 0;
                }
            }
        }
        GL20C.glUniform1i(ps[0], 0);
        if (indirect) GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    private static ByteBuffer cmdStaging = MemoryUtil.memAlloc(1 << 16);
    private static final IndirectCommands COMMANDS = new IndirectCommands();
    private static Boolean indirect;

    /** GL 4.3 multi-draw indirect: the commands read by the GPU instead of walked by the driver (-Dlab.chunkmd.noIndirect). */
    private static boolean indirect() {
        Boolean i = indirect;
        if (i == null) {
            var caps = GL.getCapabilities();
            indirect = i = (caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect) && !Boolean.getBoolean("lab.chunkmd.noIndirect");
            System.out.println("[sleep/opt] chunk multidraw: indirect " + i);
        }
        return i;
    }

    private static void queue(Arena a, Entry e, net.minecraft.core.BlockPos o, double x, double y, double z, float ox, float oy, float oz,
            RenderType layer, int[] ps) {
            if (!a.listed) {
                a.listed = true;
                a.n = 0;
                a.maxIndices = 0;
                LISTED.add(a);
            }
            if (a.n + 4 > a.drawCap) {
                if (ssbo()) a.ensureDraws(a.n + 4);
                else flush(a, layer, ps);
            }
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
                    addDraw(a, e.start + runStart * 4, (runEnd - runStart) * 6, ox, oy, oz, e.recipes ? e.vol : -1);
                    quadsDrawn += runEnd - runStart;
                    runStart = -1;
                }
            }
            quadsTotal += bq[7];
    }

    private static final double EPS = 1.0 / 64;
    private static final boolean SECTION_LISTS = Boolean.getBoolean("lab.opt.SectionListsMixin");

    private static void addDraw(Arena a, int baseVertex, int indices, float ox, float oy, float oz, int vol) {
        int i = a.n++;
        MemoryUtil.memPutInt(a.counts + 4L * i, indices);
        MemoryUtil.memPutInt(a.bases + 4L * i, baseVertex);
        a.offsets.putFloat(16 * i, ox).putFloat(16 * i + 4, oy).putFloat(16 * i + 8, oz).putFloat(16 * i + 12, vol);
        if (indices > a.maxIndices) a.maxIndices = indices;
    }

    /** Issues the arena's queued draws as one multi-draw. */
    private static void flush(Arena a, RenderType layer, int[] ps) {
        if (a.n == 0) return;
        GL20C.glUniform1i(ps[0], a.packed ? 2 : 1);
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        if (a.vaoStale || a.vao == 0) setupVao(a, layer);
        GL30C.glBindVertexArray(a.vao);
        seq.bind(a.maxIndices);   // element buffer of this VAO, large enough
        GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ubo());
        a.offsets.limit(16 * a.n).position(0);
        GL15C.glBufferSubData(GL31C.GL_UNIFORM_BUFFER, 0, a.offsets);
        a.offsets.clear();
        GL31C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, UBO_BINDING, ubo());
        if (LightVolumes.ON && ssbo()) GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, LightVolumes.BINDING, LightVolumes.buffer());
        GL32C.nglMultiDrawElementsBaseVertex(GL15C.GL_TRIANGLES, a.counts, seq.type().asGLType, a.indices, a.n, a.bases);
        GL20C.glUniform1i(ps[0], 0);
        multiDraws++;
        a.n = 0;
        a.maxIndices = 0;
    }

    private static int ubo, ssboBuf;
    private static Boolean ssbo;

    private static int ssboBuffer() {
        if (ssboBuf == 0) ssboBuf = GL15C.glGenBuffers();
        return ssboBuf;
    }

    /** Offsets in a shader storage buffer (GL 4.3 / ARB_shader_storage_buffer_object; -Dlab.chunkmd.noSsbo for the uniform block). */
    static boolean ssbo() {
        Boolean s = ssbo;
        if (s == null) {
            var caps = GL.getCapabilities();
            ssbo = s = (caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object) && !Boolean.getBoolean("lab.chunkmd.noSsbo");
            System.out.println("[sleep/opt] chunk multidraw: offsets in a storage buffer " + s);
        }
        return s;
    }

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
            boolean sb = ssbo();
            int block = sb ? GL43C.glGetProgramResourceIndex(program, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeChunkOffsets")
                    : GL31C.glGetUniformBlockIndex(program, "ProbeChunkOffsets");
            if (flag < 0 || block == GL31C.GL_INVALID_INDEX) {
                s = new int[0];
            } else {
                if (sb) GL43C.glShaderStorageBlockBinding(program, block, UBO_BINDING);
                else GL31C.glUniformBlockBinding(program, block, UBO_BINDING);
                if (sb && LightVolumes.ON) {
                    int lv = GL43C.glGetProgramResourceIndex(program, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeLightVolumes");
                    if (lv != GL31C.GL_INVALID_INDEX) GL43C.glShaderStorageBlockBinding(program, lv, LightVolumes.BINDING);
                }
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
        if (a.packed) {
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_UNSIGNED_SHORT, false, PACKED_STRIDE, 0);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 4, GL11C.GL_UNSIGNED_BYTE, true, PACKED_STRIDE, 8);
            GL20C.glEnableVertexAttribArray(2);
            GL20C.glVertexAttribPointer(2, 2, GL11C.GL_UNSIGNED_SHORT, false, PACKED_STRIDE, 12);
            GL20C.glEnableVertexAttribArray(3);
            GL30C.glVertexAttribIPointer(3, 2, GL11C.GL_UNSIGNED_BYTE, PACKED_STRIDE, 6);
        } else {
            layer.format().setupBufferState();
            if (LightVolumes.ON && a.stride == 32) {
                GL20C.glEnableVertexAttribArray(7);
                GL30C.glVertexAttribIPointer(7, 1, GL11C.GL_UNSIGNED_INT, 32, 28);
            }
        }
        a.vaoStale = false;
    }

    // ------------------------------------------------------------------ packing statistics (-Dlab.chunkmd.packStats)

    private static final boolean PACK_STATS = Boolean.getBoolean("lab.chunkmd.packStats");
    private static final int[] SHIFTS = {4, 8, 10, 11, 12, 16, 20, 23};
    private static final long[] posExact = new long[SHIFTS.length], uvExact = new long[SHIFTS.length];
    private static long statVerts, lightByte, alphaFull;
    private static float posMin = Float.MAX_VALUE, posMax = -Float.MAX_VALUE, uvMin = Float.MAX_VALUE, uvMax = -Float.MAX_VALUE;

    /** Which fields of the BLOCK format would survive a narrower encoding bit-exactly (counts per vertex). */
    private static void packStats(ByteBuffer vb, int verts) {
        long base = MemoryUtil.memAddress(vb);
        for (int i = 0; i < verts; i++) {
            long v = base + 32L * i;
            float x = MemoryUtil.memGetFloat(v), y = MemoryUtil.memGetFloat(v + 4), z = MemoryUtil.memGetFloat(v + 8);
            float u = MemoryUtil.memGetFloat(v + 16), w = MemoryUtil.memGetFloat(v + 20);
            posMin = Math.min(posMin, Math.min(x, Math.min(y, z)));
            posMax = Math.max(posMax, Math.max(x, Math.max(y, z)));
            uvMin = Math.min(uvMin, Math.min(u, w));
            uvMax = Math.max(uvMax, Math.max(u, w));
            for (int k = 0; k < SHIFTS.length; k++) {
                float sc = (float) (1 << SHIFTS[k]);
                if (x * sc == (float) Math.rint(x * sc) && y * sc == (float) Math.rint(y * sc) && z * sc == (float) Math.rint(z * sc)) posExact[k]++;
                if (u * sc == (float) Math.rint(u * sc) && w * sc == (float) Math.rint(w * sc)) uvExact[k]++;
            }
            int l = MemoryUtil.memGetInt(v + 24);
            if ((l & 0xFFFF) <= 255 && (l >>> 16) <= 255) lightByte++;
            if ((MemoryUtil.memGetInt(v + 12) >>> 24) == 255) alphaFull++;
            statVerts++;
        }
    }

    private static String packReport() {
        if (!PACK_STATS || statVerts == 0) return "";
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "; pack stats: %d vertices, pos [%s, %s], uv [%s, %s], light fits bytes %.4f %%, alpha 255 %.4f %%, exact at 2^k (pos / uv):",
                statVerts, posMin, posMax, uvMin, uvMax, 100.0 * lightByte / statVerts, 100.0 * alphaFull / statVerts));
        for (int k = 0; k < SHIFTS.length; k++)
            sb.append(String.format(java.util.Locale.ROOT, " %d: %.4f / %.4f %%", SHIFTS[k], 100.0 * posExact[k] / statVerts, 100.0 * uvExact[k] / statVerts));
        return sb.toString();
    }

    public static String report() {
        int arenas = 0;
        long verts = 0;
        long bytes = 0;
        for (var maps : List.of(ARENAS, PACKED_ARENAS))
            for (var m : maps) {
                arenas += m.size();
                for (Arena a : m.values()) {
                    verts += a.capacity;
                    bytes += (long) a.capacity * a.stride;
                }
            }
        return String.format(java.util.Locale.ROOT, "chunk multidraw: reused draws %d, packed quads %.2f %%, patched %s, multi-draw calls %d, sections by multi-draw %d, by vanilla draws %d, skipped %d, quads drawn %.1f %% (face culling %s), uploads %d, arenas %d (%.1f MB), grows %d, vanilla buffers %s",
                drawReuses, 100.0 * quadsPacked / Math.max(1, quadsPacked + quadsFull), PATCHED, multiDraws, sectionsMulti, sectionsVanilla, sectionsSkipped, 100.0 * quadsDrawn / Math.max(1, quadsTotal), FACE_CULL ? "on" : "off",
                uploads, arenas, bytes / 1048576.0, grows,
                KEEP_VANILLA || PATCHED.size() != SUPPORTED.size() ? "filled" : "not filled") + packReport();
    }
}
