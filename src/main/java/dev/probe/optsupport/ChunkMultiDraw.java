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
    public static final int MAX_DRAWS = 256;   // sections per region = offsets per uniform block
    private static final int REGION_W = 8, REGION_H = 4;
    private static final int UBO_BINDING = 7;
    /** vertex shader names patched by ChunkShaderPatchMixin */
    public static final Set<String> PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final Set<String> SUPPORTED = Set.of("rendertype_solid", "rendertype_cutout_mipped", "rendertype_cutout");
    public static long multiDraws, sectionsMulti, sectionsVanilla, sectionsSkipped, uploads, grows;

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

    static final class Entry {
        int start, vertices, indexCount;
        long origin;
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
        ByteBuffer vb = mesh.vertexBuffer();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, a.vbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) start * stride, vb);
        Entry e = new Entry();
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
            if (e == null || e.origin != o.asLong() || a.n >= MAX_DRAWS) {
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
            int i = a.n++;
            MemoryUtil.memPutInt(a.counts + 4L * i, e.indexCount);
            MemoryUtil.memPutInt(a.bases + 4L * i, e.start);
            a.offsets.putFloat(16 * i, ox).putFloat(16 * i + 4, oy).putFloat(16 * i + 8, oz);
            if (e.indexCount > a.maxIndices) a.maxIndices = e.indexCount;
        }
        if (!LISTED.isEmpty()) {
            GL20C.glUniform1i(ps[0], 1);
            RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            for (Arena a : LISTED) {
                a.listed = false;
                if (a.vaoStale || a.vao == 0) setupVao(a, layer);
                GL30C.glBindVertexArray(a.vao);
                seq.bind(a.maxIndices);   // element buffer of this VAO, large enough
                GL15C.glBindBuffer(GL31C.GL_UNIFORM_BUFFER, ubo());
                a.offsets.limit(16 * a.n).position(0);
                GL15C.glBufferSubData(GL31C.GL_UNIFORM_BUFFER, 0, a.offsets);
                a.offsets.clear();
                GL31C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, UBO_BINDING, ubo());
                GL32C.nglMultiDrawElementsBaseVertex(GL15C.GL_TRIANGLES, a.counts, seq.type().asGLType, a.indices, a.n, a.bases);
                multiDraws++;
                sectionsMulti += a.n;
            }
            GL20C.glUniform1i(ps[0], 0);
        }
        return true;
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
        return String.format(java.util.Locale.ROOT, "chunk multidraw: patched %s, multi-draw calls %d, sections by multi-draw %d, by vanilla draws %d, skipped %d, uploads %d, arenas %d (%.1f MB), grows %d, vanilla buffers %s",
                PATCHED, multiDraws, sectionsMulti, sectionsVanilla, sectionsSkipped, uploads, arenas, verts * 32 / 1048576.0, grows,
                KEEP_VANILLA || PATCHED.size() != SUPPORTED.size() ? "filled" : "not filled");
    }
}
