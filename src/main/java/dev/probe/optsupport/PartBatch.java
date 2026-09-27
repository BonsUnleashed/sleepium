package dev.probe.optsupport;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * exp/part-batch (client, render cache v3): model parts drawn from geometry kept in VRAM, one multi-draw per render
 * type; each frame only the part's matrices, light, overlay and colour are sent.
 *
 * <p>ModelPart.render's compile call is intercepted when the consumer is a batched (commuting, EntityBatch) buffer of the
 * NEW_ENTITY format whose shader is patched: the part's geometry (vanilla compile with an identity pose, recorded once:
 * position / 16, UV, normal) lives in a VRAM arena, and the call becomes a record {pose, normal matrix, colour, light,
 * overlay} in the render type's queue. When that type's batch is drawn (VertexBuffer._drawWithShader, right after the
 * vanilla draw, with the program, samplers and render state still set) the queue is one glMultiDrawElementsBaseVertex;
 * the patched entity vertex shader takes, per gl_DrawID, the record from a shader storage buffer and computes
 * position, normal (normalised and quantised to bytes as BufferBuilder does), colour, light and overlay. The draws of a
 * commuting type give the same pixels in any order, so drawing the parts after the batch's other vertices is safe.
 * The GPU computes the transform: differences in float rounding (the render cache's allowance).
 */
public final class PartBatch {
    private static final int SSBO_BINDING = 6;
    public static final Set<String> PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static long parts, draws, fallbacks, geometries;

    private PartBatch() {}

    private static Boolean capable;

    public static boolean capable() {
        Boolean c = capable;
        if (c == null) {
            GLCapabilities caps = GL.getCapabilities();
            capable = c = (caps.OpenGL46 || caps.GL_ARB_shader_draw_parameters) && (caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object);
            System.out.println("[probe/opt] part batch: " + (c ? "available" : "not available (needs shader draw parameters and storage buffers)"));
        }
        return c;
    }

    // ------------------------------------------------------------------ shader patch

    private static final Pattern IN = Pattern.compile("\\bin\\s+(\\w+)\\s+(Position|Color|UV1|UV2|Normal)\\s*;");

    /**
     * Any vertex shader with vec3 Position, vec4 Color, vec3 Normal inputs (UV1 / UV2 optional, their declared type
     * kept): main() starts with local copies of those inputs, filled from the storage buffer in part mode, and uses
     * the copies. Anything else is returned unchanged.
     */
    public static String patch(String name, String src) {
        if (!src.startsWith("#version 150") || !src.contains("void main() {")) return src;
        Map<String, String> types = new HashMap<>();
        Matcher m = IN.matcher(src);
        while (m.find()) types.put(m.group(2), m.group(1));
        if (!"vec3".equals(types.get("Position")) || !"vec4".equals(types.get("Color")) || !"vec3".equals(types.get("Normal"))) return src;
        String uv1 = types.get("UV1"), uv2 = types.get("UV2");
        if (uv1 != null && !uv1.equals("ivec2") && !uv1.equals("vec2")) return src;
        if (uv2 != null && !uv2.equals("ivec2")) return src;
        int at = src.indexOf("void main() {");
        String head = src.substring(0, at), body = src.substring(at + "void main() {".length());
        for (String v : new String[] {"Position", "Color", "Normal", "UV1", "UV2"}) body = body.replaceAll("\\b" + v + "\\b", "probe_" + v);
        StringBuilder pre = new StringBuilder();
        pre.append("struct ProbePart { mat4 pose; vec4 n0; vec4 n1; vec4 n2; vec4 color; ivec4 lo; };\n");
        pre.append("layout(std430) readonly buffer ProbeParts { ProbePart probeParts[]; };\n");
        pre.append("uniform int ProbePartMode;\n\n");
        pre.append("void main() {\n");
        pre.append("    vec3 probe_Position = Position;\n    vec4 probe_Color = Color;\n    vec3 probe_Normal = Normal;\n");
        if (uv1 != null) pre.append("    ").append(uv1).append(" probe_UV1 = UV1;\n");
        if (uv2 != null) pre.append("    ivec2 probe_UV2 = UV2;\n");
        pre.append("    if (ProbePartMode == 2) {\n");
        pre.append("        probe_Position = (probeParts[gl_DrawIDARB].pose * vec4(Position, 1.0)).xyz;\n");
        pre.append("    } else if (ProbePartMode != 0) {\n");
        pre.append("        ProbePart probe_d = probeParts[gl_DrawIDARB];\n");
        pre.append("        probe_Position = (probe_d.pose * vec4(Position, 1.0)).xyz;\n");
        pre.append("        vec3 probe_n = normalize(mat3(probe_d.n0.xyz, probe_d.n1.xyz, probe_d.n2.xyz) * Normal);\n");
        pre.append("        probe_Normal = trunc(clamp(probe_n, -1.0, 1.0) * 127.0) / 127.0;\n");   // as BufferBuilder packs it
        pre.append("        probe_Color = probe_d.color;\n");
        if (uv1 != null) pre.append("        probe_UV1 = ").append(uv1).append("(probe_d.lo.zw);\n");
        if (uv2 != null) pre.append("        probe_UV2 = probe_d.lo.xy;\n");
        pre.append("    }");
        String out = head.replaceFirst("#version 150", "#version 150\n#extension GL_ARB_shader_draw_parameters : require\n#extension GL_ARB_shader_storage_buffer_object : require")
                + pre + body;
        PATCHED.add(name);
        return out;
    }

    // ------------------------------------------------------------------ geometry arena (position 3f, uv 2f, normal 3b+pad: 24 bytes)

    static final int STRIDE = 24;
    private static int vbo, vao, capacity, used;   // in vertices
    private static boolean vaoStale = true;
    private static final Map<ModelPart, int[]> GEOMETRY = new IdentityHashMap<>();   // part -> {first vertex, quads}
    private static ByteBuffer geomScratch = MemoryUtil.memAlloc(1 << 16);

    /** A consumer that keeps position, UV and normal of what a part's compile emits (identity pose: local space). */
    static final class GeometryRecorder implements VertexConsumer {
        int n;
        float nx, ny, nz;

        void reset() {
            n = 0;
        }

        private void ensure() {
            if (geomScratch.capacity() < (n + 1) * STRIDE) {
                ByteBuffer b = MemoryUtil.memAlloc(geomScratch.capacity() * 2);
                MemoryUtil.memCopy(MemoryUtil.memAddress(geomScratch), MemoryUtil.memAddress(b), (long) n * STRIDE);
                MemoryUtil.memFree(geomScratch);
                geomScratch = b;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            ensure();
            long v = MemoryUtil.memAddress(geomScratch) + (long) n * STRIDE;
            MemoryUtil.memPutFloat(v, x);
            MemoryUtil.memPutFloat(v + 4, y);
            MemoryUtil.memPutFloat(v + 8, z);
            n++;
            return this;
        }

        private long last() {
            return MemoryUtil.memAddress(geomScratch) + (long) (n - 1) * STRIDE;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) {
            MemoryUtil.memPutFloat(last() + 12, u);
            MemoryUtil.memPutFloat(last() + 16, v);
            return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            long p = last() + 20;
            MemoryUtil.memPutByte(p, normalByte(x));
            MemoryUtil.memPutByte(p + 1, normalByte(y));
            MemoryUtil.memPutByte(p + 2, normalByte(z));
            MemoryUtil.memPutByte(p + 3, (byte) 0);
            return this;
        }
    }

    private static byte normalByte(float f) {
        return (byte) ((int) (net.minecraft.util.Mth.clamp(f, -1.0F, 1.0F) * 127.0F) & 0xFF);
    }

    private static final GeometryRecorder GEOM = new GeometryRecorder();
    private static final PoseStack IDENTITY = new PoseStack();

    /** The part's geometry in the arena (recorded once through the vanilla compile), or null if it cannot be. */
    private static int[] geometry(ModelPart part, PartCompiler compiler) {
        int[] g = GEOMETRY.get(part);
        if (g != null) return g;
        GEOM.reset();
        compiler.probe$geometry(IDENTITY.last(), GEOM);
        if (GEOM.n == 0 || GEOM.n % 4 != 0) {
            GEOMETRY.put(part, g = new int[] {-1, 0});
            return g;
        }
        if (used + GEOM.n > capacity) grow(GEOM.n);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        geomScratch.limit(GEOM.n * STRIDE).position(0);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) used * STRIDE, geomScratch);
        geomScratch.clear();
        g = new int[] {used, GEOM.n / 4};
        used += GEOM.n;
        GEOMETRY.put(part, g);
        geometries++;
        return g;
    }

    private static void grow(int needed) {
        int cap = Math.max(capacity * 2, capacity + needed + 65536);
        int nb = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, nb);
        GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, (long) cap * STRIDE, GL15C.GL_STATIC_DRAW);
        if (vbo != 0) {
            GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, vbo);
            GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, 0, 0, (long) used * STRIDE);
            GL15C.glDeleteBuffers(vbo);
        }
        vbo = nb;
        capacity = cap;
        vaoStale = true;
    }

    /** The vanilla compile of one part with a given pose into a consumer (ModelPart's private compile). */
    public interface PartCompiler {
        void probe$geometry(PoseStack.Pose pose, VertexConsumer consumer);
    }

    // ------------------------------------------------------------------ capture

    /** Per render type: the queued part records (144 bytes each: pose, normal columns, colour, light/overlay). */
    static final class Queue {
        long mem = MemoryUtil.nmemAlloc(144L * 256);
        int cap = 256, n;
        long counts = MemoryUtil.nmemAlloc(4L * 256), bases = MemoryUtil.nmemAlloc(4L * 256), indices = MemoryUtil.nmemCalloc(256, 8);
        int maxIndices;
        float[] keys = new float[64];

        void ensure() {
            if (n < cap) return;
            cap *= 2;
            mem = MemoryUtil.nmemRealloc(mem, 144L * cap);
            counts = MemoryUtil.nmemRealloc(counts, 4L * cap);
            bases = MemoryUtil.nmemRealloc(bases, 4L * cap);
            long ni = MemoryUtil.nmemCalloc(cap, 8);
            MemoryUtil.nmemFree(indices);
            indices = ni;
        }
    }

    private static final Map<RenderType, Queue> QUEUES = new IdentityHashMap<>(), RESIDENT = new IdentityHashMap<>();

    // ------------------------------------------------------------------ resident recordings (render cache in VRAM)

    /** -Dlab.rcache.resident=false: the render cache replays on the CPU even where it could draw from VRAM. */
    public static final boolean RESIDENT_ON = !"false".equals(System.getProperty("lab.rcache.resident"));
    static final ChunkMultiDraw.Arena RES_ARENA = newResArena();
    private static int resVao;
    public static long residentDraws, residentUploads;
    /** vertex ranges of collected recordings, freed on the render thread */
    static final java.util.concurrent.ConcurrentLinkedQueue<int[]> RES_FREE = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static ChunkMultiDraw.Arena newResArena() {
        ChunkMultiDraw.Arena a = new ChunkMultiDraw.Arena(0, null, false);
        a.stride = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
        return a;
    }

    /** A recorded segment of this type can be drawn from VRAM with its pose: batched, NEW_ENTITY quads, patched shader. */
    public static boolean residentOk(RenderType t, VertexConsumer vc, net.minecraft.client.renderer.MultiBufferSource buffers) {
        if (!RESIDENT_ON || !(vc instanceof BufferBuilder bb) || t.format() != DefaultVertexFormat.NEW_ENTITY || t.mode() != VertexFormat.Mode.QUADS) return false;
        if (EntityBatch.typeOf(bb) == t) return typeOk(t) && capable();
        // experiment (-Dlab.rcache.translucentResident): a sorted (translucent) type, drawn per object back to front
        return TRANSLUCENT_RESIDENT && t.sortOnUpload() && buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource bs
                && ((dev.probe.opt.BufferSourceAccessor) bs).probe$started().get(t) == bb && typeOk(t) && capable();
    }

    /**
     * -Dlab.rcache.translucentResident=true (experiment, differs from vanilla): cached segments of sorted translucent
     * types (items in frames) drawn from VRAM too, ordered per object (its origin's distance, farthest first) instead of
     * per quad across all objects; inside an object the recorded order.
     */
    public static final boolean TRANSLUCENT_RESIDENT = !"false".equals(System.getProperty("lab.rcache.translucentResident"));

    /** Uploads n vertices (NEW_ENTITY) once; returns the first vertex in the resident arena. */
    static int residentUpload(long src, int n) {
        drainFree();
        ChunkMultiDraw.Arena a = RES_ARENA;
        int start = a.alloc(n);
        if (start < 0) {
            a.grow(n);
            start = a.alloc(n);
        }
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, a.vbo);
        GL15C.nglBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) start * a.stride, (long) n * a.stride, src);
        residentUploads++;
        return start;
    }

    static void residentRelease(int start, int n) {
        RES_ARENA.release(start, n);
    }

    private static void drainFree() {
        int[] r;
        while ((r = RES_FREE.poll()) != null) RES_ARENA.release(r[0], r[1]);
    }

    /** One resident segment drawn with the pose M this frame (the batch gets an empty quad so it is drawn). */
    public static void queueResident(RenderType t, BufferBuilder bb, int start, int n, Matrix4f m) {
        dev.probe.opt.BufferBuilderAccessor a = (dev.probe.opt.BufferBuilderAccessor) bb;
        if (a.probe$vertices() == 0) for (int i = 0; i < 4; i++) bb.addVertex(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
        Queue q = RESIDENT.computeIfAbsent(t, x -> new Queue());
        q.ensure();
        m.getToAddress(q.mem + 144L * q.n);
        if (q.keys.length <= q.n) q.keys = java.util.Arrays.copyOf(q.keys, Math.max(64, q.keys.length * 2));
        q.keys[q.n] = m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32();   // the object's origin, camera-relative
        int idx = n / 4 * 6;
        MemoryUtil.memPutInt(q.counts + 4L * q.n, idx);
        MemoryUtil.memPutInt(q.bases + 4L * q.n, start);
        if (idx > q.maxIndices) q.maxIndices = idx;
        q.n++;
        residentDraws++;
    }
    private static final Map<RenderType, Boolean> TYPE_OK = new IdentityHashMap<>();

    /**
     * From ModelPart.render in place of compile: true if the part was queued (then nothing is written to the buffer).
     * Only for a buffer EntityBatch batches, of the NEW_ENTITY format, whose render type's shader is patched.
     */
    public static boolean capture(ModelPart part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, PartCompiler compiler) {
        if (!(buffer instanceof BufferBuilder bb) || !RenderSystem.isOnRenderThread()) return false;
        RenderType type = EntityBatch.typeOf(bb);
        if (type == null || type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS || !typeOk(type) || !capable()) return false;
        int[] g = geometry(part, compiler);
        if (g[0] < 0) return false;
        dev.probe.opt.BufferBuilderAccessor a = (dev.probe.opt.BufferBuilderAccessor) bb;
        if (a.probe$vertices() == 0) {   // the batch must be drawn even if every part of it is queued: one empty quad
            for (int i = 0; i < 4; i++) bb.addVertex(0, 0, 0, 0, 0, 0, overlay, light, 0, 1, 0);
        }
        Queue q = QUEUES.computeIfAbsent(type, t -> new Queue());
        q.ensure();
        long r = q.mem + 144L * q.n;
        Matrix4f m = pose.pose();
        m.getToAddress(r);
        Matrix3f nm = pose.normal();
        MemoryUtil.memPutFloat(r + 64, nm.m00());
        MemoryUtil.memPutFloat(r + 68, nm.m01());
        MemoryUtil.memPutFloat(r + 72, nm.m02());
        MemoryUtil.memPutFloat(r + 80, nm.m10());
        MemoryUtil.memPutFloat(r + 84, nm.m11());
        MemoryUtil.memPutFloat(r + 88, nm.m12());
        MemoryUtil.memPutFloat(r + 96, nm.m20());
        MemoryUtil.memPutFloat(r + 100, nm.m21());
        MemoryUtil.memPutFloat(r + 104, nm.m22());
        // colour as BufferBuilder writes it (ARGB int -> RGBA bytes), normalised as the attribute is
        MemoryUtil.memPutFloat(r + 112, (color >> 16 & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 116, (color >> 8 & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 120, (color & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 124, (color >>> 24) / 255.0F);
        MemoryUtil.memPutInt(r + 128, (short) (light & 0xFFFF));
        MemoryUtil.memPutInt(r + 132, (short) (light >> 16 & 0xFFFF));
        MemoryUtil.memPutInt(r + 136, (short) (overlay & 0xFFFF));
        MemoryUtil.memPutInt(r + 140, (short) (overlay >> 16 & 0xFFFF));
        int idx = g[1] * 6;
        MemoryUtil.memPutInt(q.counts + 4L * q.n, idx);
        MemoryUtil.memPutInt(q.bases + 4L * q.n, g[0]);
        if (idx > q.maxIndices) q.maxIndices = idx;
        q.n++;
        parts++;
        return true;
    }

    /** The render type draws with a patched shader (its shader shard's supplier, asked once). */
    private static boolean typeOk(RenderType t) {
        Boolean ok = TYPE_OK.get(t);
        if (ok == null) {
            ok = false;
            ShaderInstance s = EntityBatch.shaderOf(t);
            if (s != null && PATCHED.contains(s.getName()) && GL20C.glGetUniformLocation(s.getId(), "ProbePartMode") >= 0) ok = true;
            TYPE_OK.put(t, ok);
        }
        return ok;
    }

    // ------------------------------------------------------------------ draw

    private static RenderType current;
    private static long cmdMem, cmdCap;
    private static int cmdBuf;
    private static final java.util.HashMap<Integer, int[]> PROGRAMS = new java.util.HashMap<>();
    private static int ssbo, ssboSize;

    public static void beginType(RenderType t) {
        current = t;
    }

    public static void endType() {
        current = null;
    }

    /** From VertexBuffer._drawWithShader right after the batch's own draw: the type's queued parts. */
    public static void afterDraw(ShaderInstance shader) {
        RenderType t = current;
        if (t == null) return;
        Queue q = QUEUES.get(t), r = RESIDENT.get(t);
        boolean parts = q != null && q.n > 0, res = r != null && r.n > 0;
        if (!parts && !res) return;
        if (parts) drawQueue(shader, q, 1);
        if (res) {
            if (t.sortOnUpload()) sortBackToFront(r);
            drawQueue(shader, r, 2);
        }
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();   // vanilla's bound buffer is no longer bound
        GL30C.glBindVertexArray(0);
    }

    /** The queue's entries reordered by key, farthest first (stable). */
    private static void sortBackToFront(Queue q) {
        int n = q.n;
        if (n < 2) return;
        int[] order = QuadSort.sort(q.keys, n);
        long rec = MemoryUtil.nmemAlloc(144L * n), cnt = MemoryUtil.nmemAlloc(4L * n), bas = MemoryUtil.nmemAlloc(4L * n);
        for (int i = 0; i < n; i++) {
            int j = order[i];
            MemoryUtil.memCopy(q.mem + 144L * j, rec + 144L * i, 144);
            MemoryUtil.memPutInt(cnt + 4L * i, MemoryUtil.memGetInt(q.counts + 4L * j));
            MemoryUtil.memPutInt(bas + 4L * i, MemoryUtil.memGetInt(q.bases + 4L * j));
        }
        MemoryUtil.memCopy(rec, q.mem, 144L * n);
        MemoryUtil.memCopy(cnt, q.counts, 4L * n);
        MemoryUtil.memCopy(bas, q.bases, 4L * n);
        MemoryUtil.nmemFree(rec);
        MemoryUtil.nmemFree(cnt);
        MemoryUtil.nmemFree(bas);
    }

    private static void drawQueue(ShaderInstance shader, Queue q, int mode) {
        // looked up once per program: a query each draw makes the render thread wait for the driver's thread
        int[] st = PROGRAMS.computeIfAbsent(shader.getId(), id -> {
            int f = GL20C.glGetUniformLocation(id, "ProbePartMode");
            int b = GL43C.glGetProgramResourceIndex(id, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeParts");
            if (f >= 0 && b != GL31C.GL_INVALID_INDEX) GL43C.glShaderStorageBlockBinding(id, b, SSBO_BINDING);
            return new int[] {f, b};
        });
        int flag = st[0];
        if (flag < 0 || st[1] == GL31C.GL_INVALID_INDEX) {
            fallbacks += q.n;   // should not happen (typeOk): the parts are lost for this frame
            q.n = 0;
            return;
        }
        if (ssbo == 0) ssbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, ssbo);
        long bytes = 144L * q.n;
        GL15C.nglBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, bytes, q.mem, GL15C.GL_STREAM_DRAW);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SSBO_BINDING, ssbo);
        if (mode == 1) {
            if (vaoStale || vao == 0) setupVao();
            GL30C.glBindVertexArray(vao);
        } else {
            if (RES_ARENA.vaoStale || resVao == 0) {
                if (resVao == 0) resVao = GL30C.glGenVertexArrays();
                GL30C.glBindVertexArray(resVao);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, RES_ARENA.vbo);
                DefaultVertexFormat.NEW_ENTITY.setupBufferState();
                RES_ARENA.vaoStale = false;
            }
            GL30C.glBindVertexArray(resVao);
        }
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        seq.bind(q.maxIndices);
        GL20C.glUniform1i(flag, mode);
        // the commands in a buffer the GPU reads (the driver no longer walks every draw on the CPU)
        long bytes20 = 20L * q.n;
        if (cmdCap < bytes20) {
            if (cmdMem != 0) MemoryUtil.nmemFree(cmdMem);
            cmdCap = Math.max(bytes20, cmdCap * 2);
            cmdMem = MemoryUtil.nmemAlloc(cmdCap);
        }
        for (int i = 0; i < q.n; i++) {
            long c = cmdMem + 20L * i;
            MemoryUtil.memPutInt(c, MemoryUtil.memGetInt(q.counts + 4L * i));
            MemoryUtil.memPutInt(c + 4, 1);
            MemoryUtil.memPutInt(c + 8, 0);
            MemoryUtil.memPutInt(c + 12, MemoryUtil.memGetInt(q.bases + 4L * i));
            MemoryUtil.memPutInt(c + 16, 0);
        }
        if (cmdBuf == 0) cmdBuf = GL15C.glGenBuffers();
        GL15C.glBindBuffer(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, cmdBuf);
        GL15C.nglBufferData(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, bytes20, cmdMem, GL15C.GL_STREAM_DRAW);
        GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, seq.type().asGLType, 0L, q.n, 20);
        GL15C.glBindBuffer(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
        GL20C.glUniform1i(flag, 0);
        draws++;
        q.n = 0;
        q.maxIndices = 0;
    }

    /** Attribute indices as the entity shaders bind them (NEW_ENTITY order): Position 0, UV0 2, Normal 5. */
    private static void setupVao() {
        if (vao == 0) vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, STRIDE, 0);
        GL20C.glEnableVertexAttribArray(2);
        GL20C.glVertexAttribPointer(2, 2, GL11C.GL_FLOAT, false, STRIDE, 12);
        GL20C.glEnableVertexAttribArray(5);
        GL20C.glVertexAttribPointer(5, 3, GL11C.GL_BYTE, true, STRIDE, 20);
        vaoStale = false;
    }

    /** End of a frame's level render: nothing queued may outlive it (a type never drawn this frame). */
    public static void endFrame() {
        for (Queue q : RESIDENT.values()) {
            if (q.n > 0) fallbacks += q.n;
            q.n = 0;
            q.maxIndices = 0;
        }
        for (Queue q : QUEUES.values()) {
            if (q.n > 0) fallbacks += q.n;
            q.n = 0;
            q.maxIndices = 0;
        }
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "part batch: shaders patched %s, parts %d, multi-draws %d, geometries %d (%.1f MB), lost %d; resident draws %d, uploads %d (arena %.1f MB)",
                PATCHED, parts, draws, geometries, (double) used * STRIDE / 1048576.0, fallbacks, residentDraws, residentUploads,
                (double) RES_ARENA.capacity * RES_ARENA.stride / 1048576.0);
    }
}
