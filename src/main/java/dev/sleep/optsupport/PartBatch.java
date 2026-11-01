package dev.sleep.optsupport;

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
    private static final int SSBO_BINDING = 6, SPIN_BINDING = 5;
    /** -Dlab.opt.KineticResidentMixin: shaders get mode 5 (persistent records turned on the GPU, KineticResident) */
    public static final boolean PERSIST_ON = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.KineticResidentMixin");
    /** -Dlab.opt.LevelLitPartsMixin: shaders get mode 6 (SuperByteBuffers lit by the level: light boxes, LightBoxes) */
    public static final boolean LIT_ON = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.LevelLitPartsMixin");
    private static final boolean EXTRA = PERSIST_ON || LIT_ON;
    public static final Set<String> PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static long parts, draws, fallbacks, geometries;

    private PartBatch() {}

    private static Boolean capable;

    public static boolean capable() {
        Boolean c = capable;
        if (c == null) {
            GLCapabilities caps = GL.getCapabilities();
            capable = c = (caps.OpenGL46 || caps.GL_ARB_shader_draw_parameters) && (caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object);
            System.out.println("[sleep/opt] part batch: " + (c ? "available" : "not available (needs shader draw parameters and storage buffers)"));
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
        boolean uv0 = EXTRA && Pattern.compile("\\bin\\s+vec2\\s+UV0\\s*;").matcher(src).find();
        int at = src.indexOf("void main() {");
        String head = src.substring(0, at), body = src.substring(at + "void main() {".length());
        for (String v : new String[] {"Position", "Color", "Normal", "UV1", "UV2"}) body = body.replaceAll("\\b" + v + "\\b", "probe_" + v);
        if (uv0) body = body.replaceAll("\\bUV0\\b", "probe_UV0");
        StringBuilder pre = new StringBuilder();
        pre.append("struct ProbePart { mat4 pose; vec4 n0; vec4 n1; vec4 n2; vec4 color; ivec4 lo; };\n");
        pre.append("layout(std430) buffer ProbeParts { ProbePart probeParts[]; };\n");
        pre.append("uniform int ProbePartMode;\n");
        pre.append("uniform vec3 ProbeLight0;\nuniform vec3 ProbeLight1;\n");   // mode 4: the level's light directions (block shaders have none)
        if (EXTRA) {   // mode 5: persistent records (by base instance), their turn about an axis computed from the time; mode 6: light transform
            pre.append("struct ProbeSpin { vec4 turn; vec4 sprites; vec4 scroll; };\n");
            pre.append("layout(std430) buffer ProbeSpins { ProbeSpin probeSpins[]; };\n");
            pre.append("uniform vec3 ProbeCam;\nuniform float ProbeTime;\n");
            if (LIT_ON) pre.append(LightBoxes.GLSL);
        }
        pre.append("layout(location = 15) in float ProbeFlags;\n\n");   // part geometry's 4th normal byte (GeckoLib flat cubes)
        pre.append("void main() {\n");
        pre.append("    vec3 probe_Position = Position;\n    vec4 probe_Color = Color;\n    vec3 probe_Normal = Normal;\n");
        if (uv1 != null) pre.append("    ").append(uv1).append(" probe_UV1 = UV1;\n");
        if (uv2 != null) pre.append("    ivec2 probe_UV2 = UV2;\n");
        if (uv0) pre.append("    vec2 probe_UV0 = UV0;\n");
        pre.append("    if (ProbePartMode == 2) {\n");
        pre.append("        probe_Position = (probeParts[gl_DrawIDARB].pose * vec4(Position, 1.0)).xyz;\n");
        pre.append("    } else if (ProbePartMode != 0) {\n");
        if (EXTRA) {
            pre.append("        int probe_i = ProbePartMode == 5 ? gl_BaseInstanceARB : gl_DrawIDARB;\n");
            pre.append("        ProbePart probe_d = probeParts[probe_i];\n");
            pre.append("        vec3 probe_p = Position, probe_nin = Normal;\n");
            // Create's angle: ((time x speed x 3 / 10 + offset) % 360) degrees (Java's %: truncated), about the block's centre
            pre.append("        if (ProbePartMode == 5) {\n");
            pre.append("            ProbeSpin probe_sp = probeSpins[probe_i];\n");
            pre.append("            vec4 probe_s = probe_sp.turn;\n");
            pre.append("            if (probe_s.x > 0.5) {\n");
            pre.append("                float probe_a = ProbeTime * probe_s.y * 3.0 / 10.0 + probe_s.z;\n");
            pre.append("                probe_a = (probe_a - 360.0 * trunc(probe_a / 360.0)) / 180.0 * 3.14159265358979;\n");
            pre.append("                vec3 probe_k = probe_s.x < 1.5 ? vec3(1.0, 0.0, 0.0) : probe_s.x < 2.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);\n");
            pre.append("                float probe_c = cos(probe_a), probe_sn = sin(probe_a);\n");
            pre.append("                mat3 probe_r = probe_c * mat3(1.0) + probe_sn * mat3(0.0, probe_k.z, -probe_k.y, -probe_k.z, 0.0, probe_k.x, probe_k.y, -probe_k.x, 0.0)"
                    + " + (1.0 - probe_c) * outerProduct(probe_k, probe_k);\n");
            pre.append("                probe_p = probe_r * (Position - 0.5) + 0.5;\n");
            pre.append("                probe_nin = probe_r * Normal;\n");
            pre.append("            }\n");
            if (uv0) {   // a sprite shift (catnip's shiftUVScrolling): ((uv - original) + target) + scroll, V scrolled for the time as Create's belts
                pre.append("            vec4 probe_sc = probe_sp.scroll;\n");
                pre.append("            if (probe_sc.w > 0.5) {\n");
                pre.append("                float probe_x = probe_sc.y * ProbeTime / 504.0 + probe_s.w;\n");
                pre.append("                probe_x = (probe_x - floor(probe_x)) * probe_sc.z;\n");
                pre.append("                probe_UV0 = vec2(UV0.x - probe_sp.sprites.x + probe_sp.sprites.y + probe_sc.x, UV0.y - probe_sp.sprites.z + probe_sp.sprites.w + probe_x);\n");
                pre.append("            }\n");
            }
            pre.append("        }\n");
            pre.append("        probe_Position = (probe_d.pose * vec4(probe_p, 1.0)).xyz;\n");
            pre.append("        if (ProbePartMode == 5) probe_Position += ProbeCam;\n");
            pre.append("        vec3 probe_n = mat3(probe_d.n0.xyz, probe_d.n1.xyz, probe_d.n2.xyz) * probe_nin;\n");
        } else {
            pre.append("        ProbePart probe_d = probeParts[gl_DrawIDARB];\n");
            pre.append("        probe_Position = (probe_d.pose * vec4(Position, 1.0)).xyz;\n");
            pre.append("        vec3 probe_n = mat3(probe_d.n0.xyz, probe_d.n1.xyz, probe_d.n2.xyz) * Normal;\n");
        }
        pre.append("        if (ProbePartMode == 1) probe_n = normalize(probe_n);\n");   // mode 3 (GeckoLib bones): as emitted, not normalised
        // GeckoLib's fixInvertedFlatCube: on a flat cube's axes a negative (posed) component is flipped: abs()
        pre.append("        if (ProbePartMode == 3) { int probe_f = int(ProbeFlags + 0.5); if ((probe_f & 1) != 0) probe_n.x = abs(probe_n.x); if ((probe_f & 2) != 0) probe_n.y = abs(probe_n.y); if ((probe_f & 4) != 0) probe_n.z = abs(probe_n.z); }\n");
        pre.append("        probe_Normal = trunc(clamp(probe_n, -1.0, 1.0) * 127.0) / 127.0;\n");   // as BufferBuilder packs it
        pre.append("        probe_Color = probe_d.color;\n");
        // mode 4 (Create's SuperByteBuffer): the diffuse it computes on the CPU - min(1, (max(0, L0.n) + max(0, L1.n)) * 0.6 + 0.4)
        // with the posed, not normalised normal; unshaded vertices (flag 8) use the fake normal (0, +-1, 0); n0.w: diffuse on
        pre.append("        if (ProbePartMode >= 4) {\n");
        pre.append("            float probe_df = 1.0;\n");
        pre.append("            if (probe_d.n0.w > 0.5) {\n");
        pre.append("                vec3 probe_ln = (int(ProbeFlags + 0.5) & 8) != 0 ? vec3(0.0, probe_d.n1.w, 0.0) : probe_n;\n");
        pre.append("                probe_df = min(1.0, (max(0.0, dot(ProbeLight0, probe_ln)) + max(0.0, dot(ProbeLight1, probe_ln))) * 0.6 + 0.4);\n");
        pre.append("            }\n");
        pre.append("            probe_Color = vec4(probe_d.color.rgb * Color.rgb * probe_df, probe_d.color.a * Color.a);\n");   // the template's colour
        pre.append("        }\n");
        if (uv1 != null) pre.append("        probe_UV1 = ").append(uv1).append("(probe_d.lo.zw);\n");
        if (uv2 != null) {
            pre.append("        probe_UV2 = probe_d.lo.xy;\n");
            // SuperByteBuffer parts (BLOCK format, geometry with the template's own light as UV2): catnip's maxLight per component
            if (EXTRA) pre.append("        if (ProbePartMode >= 4) probe_UV2 = max(UV2, probe_d.lo.xy);\n");
            if (LIT_ON) {   // mode 6: the level's light where catnip looks it up - the vertex pulled 1/16 toward its block's centre, by the light transform
                pre.append("        if (ProbePartMode == 6) {\n");
                pre.append("            ProbeSpin probe_lt = probeSpins[gl_DrawIDARB];\n");
                pre.append("            vec3 probe_q = (Position - 0.5) * 15.0 / 16.0 + 0.5;\n");
                pre.append("            vec3 probe_w = vec3(dot(probe_lt.turn.xyz, probe_q) + probe_lt.turn.w, dot(probe_lt.sprites.xyz, probe_q) + probe_lt.sprites.w,"
                        + " dot(probe_lt.scroll.xyz, probe_q) + probe_lt.scroll.w);\n");
                pre.append("            probe_UV2 = max(probe_UV2, probeBoxLight(probe_d.lo.z, ivec3(floor(probe_w))));\n");
                pre.append("        }\n");
            }
        }
        pre.append("    }");
        String out = head.replaceFirst("#version 150", "#version 150\n#extension GL_ARB_shader_draw_parameters : require\n#extension GL_ARB_shader_storage_buffer_object : require\n#extension GL_ARB_explicit_attrib_location : require")
                + pre + body;
        PATCHED.add(name);
        return out;
    }

    // ------------------------------------------------------------------ geometry arena (position 3f, uv 2f, normal 3b+flags, colour 4b, light 2s: 32 bytes)

    static final int STRIDE = 32;
    private static int vbo, vao, capacity, used;   // in vertices
    private static boolean vaoStale = true;
    private static final Map<Object, int[]> GEOMETRY = new IdentityHashMap<>();   // part / bone key -> {first vertex, quads}; {-1, 0}: not supported
    private static ByteBuffer geomScratch = MemoryUtil.memAlloc(1 << 16);
    private static final Map<Object, float[]> BOUNDS = new IdentityHashMap<>();   // key -> local min xyz, max xyz (LIT_ON)

    /** A consumer that keeps position, UV and normal of what a part's compile emits (identity pose: local space). */
    /** A geometry consumer that takes per-cube flags (GeckoLib flat cubes) for the vertices that follow. */
    public interface Flagged {
        void sleep$flags(int flags);

        /** the last vertex's colour as packed bytes R, G, B, A (white unless given: SuperByteBuffer templates have their own) */
        default void sleep$rawColor(int rgba) {}
    }

    static final class GeometryRecorder implements VertexConsumer, Flagged {
        int n, flags;

        @Override
        public void sleep$flags(int f) {
            flags = f;
        }
        float nx, ny, nz;

        void reset() {
            n = 0;
            flags = 0;
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
            MemoryUtil.memPutInt(v + 24, -1);
            MemoryUtil.memPutInt(v + 28, 0);
            n++;
            return this;
        }

        @Override
        public void sleep$rawColor(int rgba) {
            MemoryUtil.memPutInt(last() + 24, rgba);
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
        @Override public VertexConsumer setUv2(int u, int v) {   // a template's own light (SuperByteBuffer)
            MemoryUtil.memPutInt(last() + 28, (u & 0xFFFF) | v << 16);
            return this;
        }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            long p = last() + 20;
            MemoryUtil.memPutByte(p, normalByte(x));
            MemoryUtil.memPutByte(p + 1, normalByte(y));
            MemoryUtil.memPutByte(p + 2, normalByte(z));
            MemoryUtil.memPutByte(p + 3, (byte) flags);
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
        compiler.sleep$geometry(IDENTITY.last(), GEOM);
        return upload(part);
    }

    /** What GEOM recorded, into the arena under this key ({-1, 0} if empty or not whole quads). */
    private static int[] upload(Object key) {
        int[] g;
        if (GEOM.n == 0 || GEOM.n % 4 != 0) {
            GEOMETRY.put(key, g = new int[] {-1, 0});
            return g;
        }
        if (used + GEOM.n > capacity) grow(GEOM.n);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        geomScratch.limit(GEOM.n * STRIDE).position(0);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) used * STRIDE, geomScratch);
        geomScratch.clear();
        g = new int[] {used, GEOM.n / 4};
        used += GEOM.n;
        GEOMETRY.put(key, g);
        if (LIT_ON) {   // the local box of the positions (a level-lit part's light box)
            float[] bd = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            long base = MemoryUtil.memAddress(geomScratch);
            for (int i = 0; i < GEOM.n; i++)
                for (int c = 0; c < 3; c++) {
                    float f = MemoryUtil.memGetFloat(base + (long) i * STRIDE + 4L * c);
                    bd[c] = Math.min(bd[c], f);
                    bd[3 + c] = Math.max(bd[3 + c], f);
                }
            BOUNDS.put(key, bd);
        }
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
        void sleep$geometry(PoseStack.Pose pose, VertexConsumer consumer);
    }

    // ------------------------------------------------------------------ capture

    /** Per render type: the queued part records (144 bytes each: pose, normal columns, colour, light/overlay). */
    static final class Queue {
        long mem = MemoryUtil.nmemAlloc(144L * 256);
        int cap = 256, n;
        long counts = MemoryUtil.nmemAlloc(4L * 256), bases = MemoryUtil.nmemAlloc(4L * 256), indices = MemoryUtil.nmemCalloc(256, 8);
        int maxIndices;
        float[] keys = new float[64];
        /** mode 6: a light transform (3 rows) per record, or 0 */
        long aux;

        void ensure() {
            if (n < cap) return;
            cap *= 2;
            mem = MemoryUtil.nmemRealloc(mem, 144L * cap);
            if (aux != 0) aux = MemoryUtil.nmemRealloc(aux, 48L * cap);
            counts = MemoryUtil.nmemRealloc(counts, 4L * cap);
            bases = MemoryUtil.nmemRealloc(bases, 4L * cap);
            long ni = MemoryUtil.nmemCalloc(cap, 8);
            MemoryUtil.nmemFree(indices);
            indices = ni;
        }
    }

    private static final Map<RenderType, Queue> QUEUES = new IdentityHashMap<>(), GEO_QUEUES = new IdentityHashMap<>(), RESIDENT = new IdentityHashMap<>(),
            SBB_QUEUES = new IdentityHashMap<>(), LIT_QUEUES = new IdentityHashMap<>();

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
                && ((dev.sleep.opt.BufferSourceAccessor) bs).sleep$started().get(t) == bb && typeOk(t) && capable();
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
        dev.sleep.opt.BufferBuilderAccessor a = (dev.sleep.opt.BufferBuilderAccessor) bb;
        if (a.sleep$vertices() == 0) for (int i = 0; i < 4; i++) bb.addVertex(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
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
        RenderType type = batchedType(buffer);
        if (type == null) return false;
        int[] g = geometry(part, compiler);
        if (g[0] < 0) return false;
        queue(QUEUES, type, (BufferBuilder) buffer, g, pose, light, overlay, color);
        parts++;
        return true;
    }

    /** A key's geometry in the arena (render thread), built once from {@code src.sleep$emit(a, b, ...)}; null if it cannot be. */
    public static int[] geometryOf(Object key, GeometrySource src, Object a, Object b) {
        int[] g = GEOMETRY.get(key);
        if (g == null) {
            GEOM.reset();
            if (src.sleep$emit(a, b, GEOM)) g = upload(key);
            else GEOMETRY.put(key, g = new int[] {-1, 0});
        }
        return g[0] < 0 ? null : g;
    }

    /** Emits a key's geometry (local space, identity pose) into the consumer; false if it cannot be drawn this way. */
    public interface GeometrySource {
        boolean sleep$emit(Object a, Object b, VertexConsumer consumer);
    }

    public static long geoParts;

    /**
     * GeckoLib bones (mode 3: normals transformed as GeckoLib emits them, not normalised): as capture, with the
     * geometry under {@code key}, built once from {@code src.sleep$emit(a, b, ...)}.
     */
    public static boolean captureGeo(Object key, GeometrySource src, Object a, Object b, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
        RenderType type = batchedType(buffer);
        if (type == null) return false;
        int[] g = GEOMETRY.get(key);
        if (g == null) {
            GEOM.reset();
            if (src.sleep$emit(a, b, GEOM)) g = upload(key);
            else GEOMETRY.put(key, g = new int[] {-1, 0});
        }
        if (g[0] < 0) return false;
        queue(GEO_QUEUES, type, (BufferBuilder) buffer, g, pose, light, overlay, color);
        geoParts++;
        return true;
    }

    public static final long[] SBB_WHY = new long[6];
    public static final java.util.Set<String> SBB_CONSUMERS = new java.util.HashSet<>(), SBB_TYPES = new java.util.HashSet<>();
    public static long sbbImpure, sbbCalls, sbbParts, sbbRefused, sbbShift, sbbLevelLight;
    /** The level's light directions as the frame set them (RenderSystem's, through an accessor the adapter sets). */
    public static java.util.function.Supplier<org.joml.Vector3f[]> lightDirections;
    private static final org.joml.Matrix4f SBB_M = new org.joml.Matrix4f();
    private static final org.joml.Matrix3f SBB_N = new org.joml.Matrix3f();

    /**
     * Create's (catnip's) SuperByteBuffer.renderInto as a part: its template mesh under {@code key} in VRAM (built once
     * through {@code src}: false when the template is not plain - per-vertex colour or light), and a record with the
     * posed matrices (input x local, as renderInto multiplies them), its colour, light and overlay, the diffuse switch
     * (n0.w) and the fake normal's y for unshaded vertices (n1.w); the shader computes the diffuse (mode 4). Only into a
     * batched quad buffer of the BLOCK format whose shader is patched.
     */
    public static boolean captureSbb(Object key, GeometrySource src, Object a, PoseStack.Pose input, PoseStack.Pose local, float r, float g, float b,
            float alpha, boolean diffuse, float fakeNormalY, int light, int overlay, VertexConsumer buffer) {
        if (!sbbTarget(key, src, a, buffer)) return false;
        SBB_M.set(input.pose()).mul(local.pose());
        SBB_N.set(input.normal()).mul(local.normal());
        queueSbb((BufferBuilder) buffer, lastSbbType, lastSbbGeo, SBB_M, SBB_N, r, g, b, alpha, diffuse, fakeNormalY, light, overlay);
        return true;
    }

    /**
     * Whether a SuperByteBuffer template into this buffer can be a part (the checks of captureSbb, its geometry uploaded
     * once): then lastSbbType / lastSbbGeo are its type and geometry. Nothing is queued.
     */
    public static boolean sbbTarget(Object key, GeometrySource src, Object a, VertexConsumer buffer) {
        int why = !(buffer instanceof BufferBuilder) ? 0 : !RenderSystem.isOnRenderThread() ? 1 : -1;
        RenderType type = why < 0 ? EntityBatch.typeOf((BufferBuilder) buffer) : null;
        if (why < 0) why = type == null ? 2 : type.format() != DefaultVertexFormat.BLOCK || type.mode() != VertexFormat.Mode.QUADS ? 3 : !typeOk(type) ? 4 : !capable() ? 5 : -1;
        if (why >= 0) {
            sbbRefused++;
            SBB_WHY[why]++;
            if (why == 0 && SBB_CONSUMERS.size() < 8) SBB_CONSUMERS.add(buffer.getClass().getName());
            if ((why == 3 || why == 4) && SBB_TYPES.size() < 8) SBB_TYPES.add(type.toString());
            return false;
        }
        int[] geo = GEOMETRY.get(key);
        if (geo == null) {
            GEOM.reset();
            if (src.sleep$emit(a, null, GEOM)) geo = upload(key);
            else GEOMETRY.put(key, geo = new int[] {-1, 0});
        }
        if (geo[0] < 0) {
            sbbRefused++;
            return false;
        }
        lastSbbType = type;
        lastSbbGeo = geo;
        return true;
    }

    public static long litParts, litRefused;
    private static final Matrix4f LIT_M = new Matrix4f();
    private static final org.joml.Vector3f LIT_V = new org.joml.Vector3f();

    /**
     * A SuperByteBuffer lit by the level (catnip's useLevelLight: contraptions, their actors) as a part: the record as
     * captureSbb's, plus its light transform ({@code lightTransform} x local; null: local) and a light box of the level
     * around where its vertices fall (LightBoxes); the shader looks the light up as catnip does (mode 6).
     */
    public static boolean captureLit(Object key, GeometrySource src, PoseStack.Pose input, PoseStack.Pose local, Matrix4f lightTransform,
            net.minecraft.world.level.BlockAndTintGetter level, float r, float g, float b, float alpha, boolean diffuse, float fakeNormalY, int light,
            int overlay, VertexConsumer buffer) {
        if (!sbbTarget(key, src, null, buffer)) return false;
        float[] bd = BOUNDS.get(key);
        if (bd == null) return false;
        if (lightTransform != null) LIT_M.set(lightTransform).mul(local.pose());
        else LIT_M.set(local.pose());
        // the box of where the pulled-in vertices fall in the level: the local box's corners through the light transform
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            LIT_V.set(((c & 1) == 0 ? bd[0] : bd[3]) - 0.5f, ((c & 2) == 0 ? bd[1] : bd[4]) - 0.5f, ((c & 4) == 0 ? bd[2] : bd[5]) - 0.5f)
                    .mul(15f / 16f).add(0.5f, 0.5f, 0.5f);
            LIT_M.transformPosition(LIT_V);
            x0 = Math.min(x0, LIT_V.x);
            y0 = Math.min(y0, LIT_V.y);
            z0 = Math.min(z0, LIT_V.z);
            x1 = Math.max(x1, LIT_V.x);
            y1 = Math.max(y1, LIT_V.y);
            z1 = Math.max(z1, LIT_V.z);
        }
        int box = LightBoxes.boxFor(level, (int) Math.floor(x0), (int) Math.floor(y0), (int) Math.floor(z0), (int) Math.floor(x1), (int) Math.floor(y1),
                (int) Math.floor(z1));
        if (box < 0) {
            litRefused++;
            return false;
        }
        SBB_M.set(input.pose()).mul(local.pose());
        SBB_N.set(input.normal()).mul(local.normal());
        Queue q = slot(LIT_QUEUES, lastSbbType, (BufferBuilder) buffer);
        if (q.aux == 0) q.aux = MemoryUtil.nmemAlloc(48L * q.cap);
        long a = q.aux + 48L * q.n;
        MemoryUtil.memPutFloat(a, LIT_M.m00());   // rows: world = M x p
        MemoryUtil.memPutFloat(a + 4, LIT_M.m10());
        MemoryUtil.memPutFloat(a + 8, LIT_M.m20());
        MemoryUtil.memPutFloat(a + 12, LIT_M.m30());
        MemoryUtil.memPutFloat(a + 16, LIT_M.m01());
        MemoryUtil.memPutFloat(a + 20, LIT_M.m11());
        MemoryUtil.memPutFloat(a + 24, LIT_M.m21());
        MemoryUtil.memPutFloat(a + 28, LIT_M.m31());
        MemoryUtil.memPutFloat(a + 32, LIT_M.m02());
        MemoryUtil.memPutFloat(a + 36, LIT_M.m12());
        MemoryUtil.memPutFloat(a + 40, LIT_M.m22());
        MemoryUtil.memPutFloat(a + 44, LIT_M.m32());
        writeSbbRecord(q.mem + 144L * q.n, SBB_M, SBB_N, r, g, b, alpha, diffuse, fakeNormalY, light, box);   // BLOCK shaders read no overlay: lo.z = the box
        commit(q, lastSbbGeo[0], lastSbbGeo[1]);
        litParts++;
        return true;
    }

    /** A SuperByteBuffer record (queueSbb's layout); lo.z / lo.w hold the overlay's halves (or, mode 6, lo.z the light box). */
    private static void writeSbbRecord(long rec, Matrix4f m, Matrix3f nm, float r, float g, float b, float alpha, boolean diffuse, float fakeNormalY,
            int light, int overlay) {
        m.getToAddress(rec);
        MemoryUtil.memPutFloat(rec + 64, nm.m00());
        MemoryUtil.memPutFloat(rec + 68, nm.m01());
        MemoryUtil.memPutFloat(rec + 72, nm.m02());
        MemoryUtil.memPutFloat(rec + 76, diffuse ? 1.0F : 0.0F);
        MemoryUtil.memPutFloat(rec + 80, nm.m10());
        MemoryUtil.memPutFloat(rec + 84, nm.m11());
        MemoryUtil.memPutFloat(rec + 88, nm.m12());
        MemoryUtil.memPutFloat(rec + 92, fakeNormalY);
        MemoryUtil.memPutFloat(rec + 96, nm.m20());
        MemoryUtil.memPutFloat(rec + 100, nm.m21());
        MemoryUtil.memPutFloat(rec + 104, nm.m22());
        MemoryUtil.memPutFloat(rec + 108, 0.0F);
        MemoryUtil.memPutFloat(rec + 112, r);
        MemoryUtil.memPutFloat(rec + 116, g);
        MemoryUtil.memPutFloat(rec + 120, b);
        MemoryUtil.memPutFloat(rec + 124, alpha);
        MemoryUtil.memPutInt(rec + 128, light & 0xFFFF);
        MemoryUtil.memPutInt(rec + 132, light >>> 16 & 0xFFFF);
        MemoryUtil.memPutInt(rec + 136, overlay & 0xFFFF);
        MemoryUtil.memPutInt(rec + 140, overlay >>> 16 & 0xFFFF);
    }

    /** A recorder of the SuperByteBuffer parts a block entity's draw makes (KineticMemo), asked by SbbPartsMixin. */
    public interface SbbRecorder {
        boolean recording();

        void captured(Object sbb, PoseStack.Pose input, PoseStack.Pose local, float r, float g, float b, float a, boolean diffuse, float fakeY,
                int light, int overlay, RenderType type, int[] geo);

        /** the draw made something that is not a part */
        void cpu();

        /**
         * A part with a scrolling sprite shift (drawn on the CPU path now: its vertexCount vertices are expected), kept with
         * the shift's sprites and scroll for a resident draw; recorders that cannot keep it call cpu().
         */
        default void capturedShifted(Object sbb, PoseStack.Pose input, PoseStack.Pose local, float r, float g, float b, float a, boolean diffuse,
                float fakeY, int light, int overlay, RenderType type, int[] geo, Object spriteShift, float scrollU, float scrollV, int vertexCount) {
            cpu();
        }

        default String report() {
            return "";
        }
    }

    public static volatile SbbRecorder sbbRecorder;

    /** Vertices of the empty quads slot() put into batches (a memo's "nothing else drawn" check discounts them). */
    public static long emptyQuadVertices;

    /** The type and geometry of the last captureSbb that succeeded (KineticMemo keeps them). */
    public static RenderType lastSbbType;
    public static int[] lastSbbGeo;

    /** One SuperByteBuffer part record with its final matrices into a batched BLOCK-format builder of this type. */
    public static void queueSbb(BufferBuilder bb, RenderType type, int[] geo, org.joml.Matrix4f m, org.joml.Matrix3f nm, float r, float g, float b,
            float alpha, boolean diffuse, float fakeNormalY, int light, int overlay) {
        Queue q = slot(SBB_QUEUES, type, bb);
        long rec = q.mem + 144L * q.n;
        writeSbbRecord(rec, m, nm, r, g, b, alpha, diffuse, fakeNormalY, light, overlay);
        commit(q, geo[0], geo[1]);
        sbbParts++;
    }

    /** The batched builder of a render type in this buffer source when PartBatch can draw SuperByteBuffer parts into it. */
    public static BufferBuilder sbbBuilder(net.minecraft.client.renderer.MultiBufferSource buffers, RenderType type) {
        VertexConsumer vc = buffers.getBuffer(type);
        return vc instanceof BufferBuilder bb && EntityBatch.typeOf(bb) == type ? bb : null;
    }

    // ------------------------------------------------------------------ persistent records (mode 5, KineticResident)

    /**
     * Records that stay in VRAM while their owner does not change (144 bytes as above, the pose relative to an anchor
     * the frame's ProbeCam moves to the camera), with a spin each (axis 1..3 or 0, speed, angle offset in degrees: the
     * shader turns the part for ProbeTime as Create does). A frame sends only the commands of the ones it draws.
     */
    private static long pMem = MemoryUtil.nmemAlloc(144L * 1024), pSpin = MemoryUtil.nmemAlloc(48L * 1024);   // SPIN_BYTES each
    private static int pCap = 1024, pUsed, pFreeN, pLo = Integer.MAX_VALUE, pHi = -1, pSsbo, pSpinSsbo, pGpuCap;
    private static int[] pFree = new int[256];
    /** a record's spin: turn (axis, speed, angle offset, scroll phase), sprites (original u0, target u0, original v0,
     *  target v0), scroll (u shift, V speed, V scale, on) */
    private static final long SPIN_BYTES = 48;
    /** the frame's anchor-to-camera offset and Create's render time (set before the level's block entities) */
    public static float persistCamX, persistCamY, persistCamZ, persistTime;
    public static long persistDraws, persistUploads;

    static final class PQueue {
        long cmds = MemoryUtil.nmemAlloc(20L * 256);
        int cap = 256, n, maxIndices;
    }

    private static final Map<RenderType, PQueue> PERSIST = new IdentityHashMap<>();

    public static int persistAlloc() {
        if (pFreeN > 0) return pFree[--pFreeN];
        if (pUsed == pCap) {
            pCap *= 2;
            pMem = MemoryUtil.nmemRealloc(pMem, 144L * pCap);
            pSpin = MemoryUtil.nmemRealloc(pSpin, SPIN_BYTES * pCap);
        }
        return pUsed++;
    }

    public static void persistFree(int i) {
        if (pFreeN == pFree.length) pFree = java.util.Arrays.copyOf(pFree, pFreeN * 2);
        pFree[pFreeN++] = i;
    }

    /** Record i: as queueSbb writes it, and its spin (axis 0: not turned; sprites null: no UV shift). */
    public static void persistWrite(int i, Matrix4f m, Matrix3f nm, float r, float g, float b, float alpha, boolean diffuse, float fakeNormalY,
            int light, int overlay, int axis, float speed, float offsetDeg, float[] sprites, float scrollU, float scrollSpeed, float scrollPhase,
            float scrollScale) {
        long rec = pMem + 144L * i;
        m.getToAddress(rec);
        MemoryUtil.memPutFloat(rec + 64, nm.m00());
        MemoryUtil.memPutFloat(rec + 68, nm.m01());
        MemoryUtil.memPutFloat(rec + 72, nm.m02());
        MemoryUtil.memPutFloat(rec + 76, diffuse ? 1.0F : 0.0F);
        MemoryUtil.memPutFloat(rec + 80, nm.m10());
        MemoryUtil.memPutFloat(rec + 84, nm.m11());
        MemoryUtil.memPutFloat(rec + 88, nm.m12());
        MemoryUtil.memPutFloat(rec + 92, fakeNormalY);
        MemoryUtil.memPutFloat(rec + 96, nm.m20());
        MemoryUtil.memPutFloat(rec + 100, nm.m21());
        MemoryUtil.memPutFloat(rec + 104, nm.m22());
        MemoryUtil.memPutFloat(rec + 108, 0.0F);
        MemoryUtil.memPutFloat(rec + 112, r);
        MemoryUtil.memPutFloat(rec + 116, g);
        MemoryUtil.memPutFloat(rec + 120, b);
        MemoryUtil.memPutFloat(rec + 124, alpha);
        MemoryUtil.memPutInt(rec + 128, light & 0xFFFF);
        MemoryUtil.memPutInt(rec + 132, light >>> 16 & 0xFFFF);
        MemoryUtil.memPutInt(rec + 136, overlay & 0xFFFF);
        MemoryUtil.memPutInt(rec + 140, overlay >>> 16 & 0xFFFF);
        long s = pSpin + SPIN_BYTES * i;
        MemoryUtil.memPutFloat(s, axis);
        MemoryUtil.memPutFloat(s + 4, speed);
        MemoryUtil.memPutFloat(s + 8, offsetDeg);
        MemoryUtil.memPutFloat(s + 12, scrollPhase);
        for (int k = 0; k < 4; k++) MemoryUtil.memPutFloat(s + 16 + 4 * k, sprites != null ? sprites[k] : 0.0F);
        MemoryUtil.memPutFloat(s + 32, scrollU);
        MemoryUtil.memPutFloat(s + 36, scrollSpeed);
        MemoryUtil.memPutFloat(s + 40, scrollScale);
        MemoryUtil.memPutFloat(s + 44, sprites != null ? 1.0F : 0.0F);
        if (i < pLo) pLo = i;
        if (i > pHi) pHi = i;
    }

    /** The command queue of a type's persistent records (kept by their owner: no lookup per draw). */
    public static Object persistQueue(RenderType type) {
        PQueue q = PERSIST.get(type);
        if (q == null) PERSIST.put(type, q = new PQueue());
        return q;
    }

    /** True when the queue has no command yet in its type's current batch (then the batch needs persistBatch first). */
    public static boolean persistFirst(Object queue) {
        return ((PQueue) queue).n == 0;
    }

    /** The type's batch gets its empty quad so it is drawn (and with it the queue: afterDraw). */
    public static void persistBatch(BufferBuilder bb) {
        emptyQuad(bb);
    }

    /** Draws persistent record rec (geometry first, quads) with its type's batch this frame. */
    public static void queuePersistent(Object queue, int first, int quads, int rec) {
        PQueue q = (PQueue) queue;
        if (q.n == q.cap) {
            q.cap *= 2;
            q.cmds = MemoryUtil.nmemRealloc(q.cmds, 20L * q.cap);
        }
        long c = q.cmds + 20L * q.n++;
        int idx = quads * 6;
        MemoryUtil.memPutInt(c, idx);
        MemoryUtil.memPutInt(c + 4, 1);
        MemoryUtil.memPutInt(c + 8, 0);
        MemoryUtil.memPutInt(c + 12, first);
        MemoryUtil.memPutInt(c + 16, rec);
        if (idx > q.maxIndices) q.maxIndices = idx;
        persistDraws++;
    }

    /** The records written since the last frame to VRAM (all of them when the store grew). */
    private static void persistUpload() {
        if (pGpuCap < pCap) {
            if (pSsbo == 0) {
                pSsbo = GL15C.glGenBuffers();
                pSpinSsbo = GL15C.glGenBuffers();
            }
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, pSsbo);
            GL15C.nglBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 144L * pCap, pMem, GL15C.GL_DYNAMIC_DRAW);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, pSpinSsbo);
            GL15C.nglBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, SPIN_BYTES * pCap, pSpin, GL15C.GL_DYNAMIC_DRAW);
            pGpuCap = pCap;
            persistUploads++;
        } else if (pHi >= pLo) {
            int n = pHi - pLo + 1;
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, pSsbo);
            GL15C.nglBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 144L * pLo, 144L * n, pMem + 144L * pLo);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, pSpinSsbo);
            GL15C.nglBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, SPIN_BYTES * pLo, SPIN_BYTES * n, pSpin + SPIN_BYTES * pLo);
            persistUploads++;
        }
        pLo = Integer.MAX_VALUE;
        pHi = -1;
    }

    private static void drawPersistent(ShaderInstance shader, PQueue q) {
        int[] st = programState(shader);
        if (st[0] < 0 || st[1] == GL31C.GL_INVALID_INDEX || st[4] < 0) {
            fallbacks += q.n;
            q.n = 0;
            return;
        }
        persistUpload();
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SSBO_BINDING, pSsbo);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SPIN_BINDING, pSpinSsbo);
        if (vaoStale || blockVao == 0) setupVao();
        GL30C.glBindVertexArray(blockVao);
        lightUniforms(st);
        GL20C.glUniform3f(st[4], persistCamX, persistCamY, persistCamZ);
        GL20C.glUniform1f(st[5], persistTime);
        RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        seq.bind(q.maxIndices);
        GL20C.glUniform1i(st[0], 5);
        long cmdAt = COMMANDS.upload(q.cmds, 20L * q.n);
        GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, seq.type().asGLType, cmdAt, q.n, 20);
        if (IndirectDiag.ON) IndirectDiag.after("parts 5", seq.type().asGLType, cmdAt, q.n, 20);
        GL15C.glBindBuffer(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
        GL20C.glUniform1i(st[0], 0);
        draws++;
        q.n = 0;
        q.maxIndices = 0;
    }

    /** The buffer's render type when it is a batch (EntityBatch) PartBatch can draw from VRAM, on the render thread. */
    private static RenderType batchedType(VertexConsumer buffer) {
        if (!(buffer instanceof BufferBuilder bb) || !RenderSystem.isOnRenderThread()) return null;
        RenderType type = EntityBatch.typeOf(bb);
        if (type == null || type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS || !typeOk(type) || !capable()) return null;
        return type;
    }

    private static void queue(Map<RenderType, Queue> queues, RenderType type, BufferBuilder bb, int[] g, PoseStack.Pose pose, int light, int overlay, int color) {
        Queue q = slot(queues, type, bb);
        writeRecord(q.mem + 144L * q.n, pose, light, overlay, color, Coatings.rtCoat, Coatings.rtCoat2, Coatings.rtHeight);
        commit(q, g[0], g[1]);
    }

    /** The type's queue with room for one more record (the batch gets its empty quad if it has no vertices yet). */
    private static Queue slot(Map<RenderType, Queue> queues, RenderType type, BufferBuilder bb) {
        emptyQuad(bb);
        Queue q = queues.computeIfAbsent(type, t -> new Queue());
        q.ensure();
        return q;
    }

    /** The batch must be drawn even if every part of it is queued: one empty quad if it has no vertices yet. */
    private static void emptyQuad(BufferBuilder bb) {
        if (((dev.sleep.opt.BufferBuilderAccessor) bb).sleep$vertices() == 0) {
            for (int i = 0; i < 4; i++) bb.addVertex(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
            emptyQuadVertices += 4;
        }
    }

    private static void commit(Queue q, int first, int quads) {
        int idx = quads * 6;
        MemoryUtil.memPutInt(q.counts + 4L * q.n, idx);
        MemoryUtil.memPutInt(q.bases + 4L * q.n, first);
        if (idx > q.maxIndices) q.maxIndices = idx;
        q.n++;
    }

    /** One 144-byte record: pose, normal matrix columns (vec4 each: their w the coatings - n0.w / n1.w the packed amounts, n2.w
     *  the entity's height, Coatings), colour (RGBA floats), light / overlay (ints). */
    static void writeRecord(long r, PoseStack.Pose pose, int light, int overlay, int color, float coat, float coat2, float height) {
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
        MemoryUtil.memPutFloat(r + 76, coat);
        MemoryUtil.memPutFloat(r + 92, coat2);
        MemoryUtil.memPutFloat(r + 108, height);
        // colour as BufferBuilder writes it (ARGB int -> RGBA bytes), normalised as the attribute is
        MemoryUtil.memPutFloat(r + 112, (color >> 16 & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 116, (color >> 8 & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 120, (color & 255) / 255.0F);
        MemoryUtil.memPutFloat(r + 124, (color >>> 24) / 255.0F);
        MemoryUtil.memPutInt(r + 128, (short) (light & 0xFFFF));
        MemoryUtil.memPutInt(r + 132, (short) (light >> 16 & 0xFFFF));
        MemoryUtil.memPutInt(r + 136, (short) (overlay & 0xFFFF));
        MemoryUtil.memPutInt(r + 140, (short) (overlay >> 16 & 0xFFFF));
    }

    // ------------------------------------------------------------------ GeckoLib bones from ParallelRender's workers

    /**
     * A worker job's bone records ({@link #captureGeoInJob}), queued by the render thread when the job is merged
     * ({@link #mergeJob}). Kept per job object, reused (no garbage).
     */
    public static final class JobParts {
        RenderType[] types = new RenderType[64];
        int[] first = new int[64], quads = new int[64];
        long mem = MemoryUtil.nmemAlloc(144L * 64);
        int n, cap = 64;
        /** the job's entity's coatings (packed) and height, for its records (Coatings) */
        public float coat, coat2, height;

        public void clear() {
            n = 0;
        }

        public int size() {
            return n;
        }

        long next(RenderType t, int[] g) {
            if (n == cap) {
                cap *= 2;
                types = java.util.Arrays.copyOf(types, cap);
                first = java.util.Arrays.copyOf(first, cap);
                quads = java.util.Arrays.copyOf(quads, cap);
                mem = MemoryUtil.nmemRealloc(mem, 144L * cap);
            }
            types[n] = t;
            first[n] = g[0];
            quads[n] = g[1];
            return mem + 144L * n++;
        }
    }

    /** read by workers: render types PartBatch draws (batched, NEW_ENTITY quads, patched shader) and bone geometries */
    private static volatile java.util.Set<RenderType> workerTypes = java.util.Set.of();
    private static volatile Map<Object, int[]> geoSnapshot = new IdentityHashMap<>();
    private static final java.util.Set<RenderType> ASKED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.Set<Object> ASKED_GEO = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    public static long jobBones, jobMerged, jobRefused;

    /**
     * Inside a ParallelRender job: the bone becomes a record in the job, when its render type and geometry are known
     * (else they are prepared on the render thread for the next frame, through the job's journal, and the bone is
     * drawn the CPU way this time).
     */
    public static boolean captureGeoInJob(Object key, GeometrySource src, Object a, Object b, PoseStack.Pose pose, RenderType type, JobParts parts, int light, int overlay, int color) {
        if (!workerTypes.contains(type)) {
            if (ASKED.add(type)) ParallelRender.journal(() -> learnType(type));
            return false;
        }
        int[] g = geoSnapshot.get(key);
        if (g == null) {
            if (ASKED_GEO.add(key)) ParallelRender.journal(() -> {
                ASKED_GEO.remove(key);
                if (GEOMETRY.get(key) == null) {
                    GEOM.reset();
                    if (src.sleep$emit(a, b, GEOM)) upload(key);
                    else GEOMETRY.put(key, new int[] {-1, 0});
                }
                publishGeo(key);
            });
            return false;
        }
        if (g[0] < 0) return false;
        writeRecord(parts.next(type, g), pose, light, overlay, color, parts.coat, parts.coat2, parts.height);
        jobBones++;
        return true;
    }

    /** Render thread: whether PartBatch can draw this type from VRAM (checked here: GL queries); published for workers. */
    private static void learnType(RenderType type) {
        ASKED.remove(type);
        if (EntityBatch.commutes(type) && type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS && typeOk(type) && capable()) {
            java.util.Set<RenderType> s = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            s.addAll(workerTypes);
            s.add(type);
            workerTypes = s;
        }
    }

    private static void publishGeo(Object key) {
        int[] g = GEOMETRY.get(key);
        if (g == null) return;
        Map<Object, int[]> m = new IdentityHashMap<>(geoSnapshot);
        m.put(key, g);
        geoSnapshot = m;
    }

    /**
     * At the job's merge (render thread): true if every record's type is a batch here (then they are queued), false if
     * not (nothing queued: the caller renders the object the vanilla way instead).
     */
    public static boolean mergeJob(JobParts parts, net.minecraft.client.renderer.MultiBufferSource buffers) {
        // the buffer is looked up once per run of records of one type (an object's bones are mostly one type)
        RenderType last = null;
        for (int i = 0; i < parts.n; i++) {
            if (parts.types[i] == last) continue;
            last = parts.types[i];
            if (!(buffers.getBuffer(last) instanceof BufferBuilder bb) || EntityBatch.typeOf(bb) != last) {
                jobRefused++;
                parts.n = 0;
                return false;
            }
        }
        last = null;
        BufferBuilder bb = null;
        Queue q = null;
        for (int i = 0; i < parts.n; i++) {
            if (parts.types[i] != last) {
                last = parts.types[i];
                bb = (BufferBuilder) buffers.getBuffer(last);
                q = GEO_QUEUES.get(last);
            }
            if (q == null || q.n == q.cap || ((dev.sleep.opt.BufferBuilderAccessor) bb).sleep$vertices() == 0) q = slot(GEO_QUEUES, last, bb);
            MemoryUtil.memCopy(parts.mem + 144L * i, q.mem + 144L * q.n, 144);
            commit(q, parts.first[i], parts.quads[i]);
        }
        jobMerged += parts.n;
        geoParts += parts.n;
        parts.n = 0;
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
    private static final IndirectCommands COMMANDS = new IndirectCommands();
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
        Queue q = QUEUES.get(t), r = RESIDENT.get(t), gq = GEO_QUEUES.get(t), sq = SBB_QUEUES.get(t);
        PQueue pq = PERSIST.isEmpty() ? null : PERSIST.get(t);
        Queue lq = LIT_QUEUES.isEmpty() ? null : LIT_QUEUES.get(t);
        boolean parts = q != null && q.n > 0, res = r != null && r.n > 0, geo = gq != null && gq.n > 0, sbb = sq != null && sq.n > 0, per = pq != null && pq.n > 0,
                lit = lq != null && lq.n > 0;
        if (!parts && !res && !geo && !sbb && !per && !lit) return;
        if (lit) drawQueue(shader, lq, 6);
        if (parts) drawQueue(shader, q, 1);
        if (geo) drawQueue(shader, gq, 3);
        if (sbb) drawQueue(shader, sq, 4);
        if (per) drawPersistent(shader, pq);
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

    /** {mode, ProbeParts block, ProbeLight0, ProbeLight1, ProbeCam, ProbeTime}: looked up once per program (a query each
     *  draw makes the render thread wait for the driver's thread). */
    private static int[] programState(ShaderInstance shader) {
        return PROGRAMS.computeIfAbsent(shader.getId(), id -> {
            int f = GL20C.glGetUniformLocation(id, "ProbePartMode");
            int b = GL43C.glGetProgramResourceIndex(id, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeParts");
            if (f >= 0 && b != GL31C.GL_INVALID_INDEX) GL43C.glShaderStorageBlockBinding(id, b, SSBO_BINDING);
            int cam = -1, time = -1, boxes = GL31C.GL_INVALID_INDEX;
            if (LIT_ON) {
                boxes = GL43C.glGetProgramResourceIndex(id, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeBoxes");
                int sp = GL43C.glGetProgramResourceIndex(id, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeSpins");
                if (boxes != GL31C.GL_INVALID_INDEX && sp != GL31C.GL_INVALID_INDEX) {
                    GL43C.glShaderStorageBlockBinding(id, boxes, LightBoxes.BINDING);
                    GL43C.glShaderStorageBlockBinding(id, sp, SPIN_BINDING);
                } else boxes = GL31C.GL_INVALID_INDEX;
            }
            if (PERSIST_ON) {
                int s = GL43C.glGetProgramResourceIndex(id, GL43C.GL_SHADER_STORAGE_BLOCK, "ProbeSpins");
                if (s != GL31C.GL_INVALID_INDEX) {
                    GL43C.glShaderStorageBlockBinding(id, s, SPIN_BINDING);
                    cam = GL20C.glGetUniformLocation(id, "ProbeCam");
                    time = GL20C.glGetUniformLocation(id, "ProbeTime");
                }
            }
            return new int[] {f, b, GL20C.glGetUniformLocation(id, "ProbeLight0"), GL20C.glGetUniformLocation(id, "ProbeLight1"), cam, time, boxes};
        });
    }

    private static void lightUniforms(int[] st) {
        org.joml.Vector3f[] ld = lightDirections != null ? lightDirections.get() : null;
        if (ld != null && st[2] >= 0) {
            L0.set(ld[0]).normalize();
            L1.set(ld[1]).normalize();
            GL20C.glUniform3f(st[2], L0.x, L0.y, L0.z);
            GL20C.glUniform3f(st[3], L1.x, L1.y, L1.z);
        }
    }

    private static void drawQueue(ShaderInstance shader, Queue q, int mode) {
        int[] st = programState(shader);
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
        if (mode == 6) {   // level-lit SuperByteBuffers: their light transforms (spin binding) and the light boxes
            if (st[6] == GL31C.GL_INVALID_INDEX) {
                fallbacks += q.n;
                q.n = 0;
                return;
            }
            if (auxSsbo == 0) auxSsbo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, auxSsbo);
            GL15C.nglBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 48L * q.n, q.aux, GL15C.GL_STREAM_DRAW);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SPIN_BINDING, auxSsbo);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, LightBoxes.BINDING, LightBoxes.buffer());
        }
        if (mode == 4 || mode == 6) {   // SuperByteBuffer templates into a BLOCK-format type: its attribute order, the light directions
            if (vaoStale || blockVao == 0) setupVao();
            GL30C.glBindVertexArray(blockVao);
            lightUniforms(st);
        } else if (mode != 2) {   // part geometry (modes 1, 3); mode 2 draws resident recordings
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
        long cmdAt = COMMANDS.upload(cmdMem, bytes20);
        boolean mat = mode != 2 && Materials.begin(shader, current);   // a material mask beside the type's texture
        GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, seq.type().asGLType, cmdAt, q.n, 20);
        if (IndirectDiag.ON) IndirectDiag.after("parts " + mode, seq.type().asGLType, cmdAt, q.n, 20);
        if (mat) Materials.end(shader);
        GL15C.glBindBuffer(org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
        GL20C.glUniform1i(flag, 0);
        draws++;
        q.n = 0;
        q.maxIndices = 0;
    }

    private static int blockVao, auxSsbo;
    private static final org.joml.Vector3f L0 = new org.joml.Vector3f(), L1 = new org.joml.Vector3f();

    /** Attribute indices as the entity shaders bind them (NEW_ENTITY order): Position 0, UV0 2, Normal 5; and for BLOCK
     *  shaders (Position 0, UV0 2, Normal 4) a second array over the same geometry. */
    private static void setupVao() {
        if (blockVao == 0) blockVao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(blockVao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, STRIDE, 0);
        GL20C.glEnableVertexAttribArray(2);
        GL20C.glVertexAttribPointer(2, 2, GL11C.GL_FLOAT, false, STRIDE, 12);
        GL20C.glEnableVertexAttribArray(4);
        GL20C.glVertexAttribPointer(4, 3, GL11C.GL_BYTE, true, STRIDE, 20);
        GL20C.glEnableVertexAttribArray(1);   // Color: the template's per-vertex colour (mode 4)
        GL20C.glVertexAttribPointer(1, 4, GL11C.GL_UNSIGNED_BYTE, true, STRIDE, 24);
        GL20C.glEnableVertexAttribArray(15);
        GL20C.glVertexAttribPointer(15, 1, GL11C.GL_UNSIGNED_BYTE, false, STRIDE, 23);
        GL20C.glEnableVertexAttribArray(3);   // UV2: the template's own light (max with the record's on the GPU)
        GL30C.glVertexAttribIPointer(3, 2, GL11C.GL_SHORT, STRIDE, 28);
        if (vao == 0) vao = GL30C.glGenVertexArrays();
        GL30C.glBindVertexArray(vao);
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, STRIDE, 0);
        GL20C.glEnableVertexAttribArray(2);
        GL20C.glVertexAttribPointer(2, 2, GL11C.GL_FLOAT, false, STRIDE, 12);
        GL20C.glEnableVertexAttribArray(5);
        GL20C.glVertexAttribPointer(5, 3, GL11C.GL_BYTE, true, STRIDE, 20);
        GL20C.glEnableVertexAttribArray(15);   // ProbeFlags (the normal's pad byte)
        GL20C.glVertexAttribPointer(15, 1, GL11C.GL_UNSIGNED_BYTE, false, STRIDE, 23);
        vaoStale = false;
    }

    /** End of a frame's level render: nothing queued may outlive it (a type never drawn this frame). */
    public static void endFrame() {
        ASKED.clear();   // a request journaled by a job that then failed was dropped: asked again next frame
        ASKED_GEO.clear();
        for (Queue q : RESIDENT.values()) {
            if (q.n > 0) fallbacks += q.n;
            q.n = 0;
            q.maxIndices = 0;
        }
        for (Map<RenderType, Queue> m : java.util.List.of(QUEUES, GEO_QUEUES, SBB_QUEUES, LIT_QUEUES)) {
            for (Queue q : m.values()) {
                if (q.n > 0) fallbacks += q.n;
                q.n = 0;
                q.maxIndices = 0;
            }
        }
        for (PQueue q : PERSIST.values()) {
            if (q.n > 0) fallbacks += q.n;
            q.n = 0;
            q.maxIndices = 0;
        }
    }

    public static String report() {
        SbbRecorder rcd = sbbRecorder;
        return (rcd != null ? rcd.report() + "; " : "") + String.format(java.util.Locale.ROOT, "part batch: SuperByteBuffer calls %d, parts %d (not taken %d %s %s %s, UV shift %d, level light %d); shaders patched %s, parts %d, GeckoLib bones %d (from workers %d, jobs refused at merge %d), multi-draws %d, geometries %d (%.1f MB), lost %d; resident draws %d, uploads %d (arena %.1f MB); persistent records %d, draws %d, uploads %d; level-lit parts %d (refused %d); %s",
                sbbCalls, sbbParts, sbbRefused, java.util.Arrays.toString(SBB_WHY), SBB_CONSUMERS, SBB_TYPES, sbbShift, sbbLevelLight, PATCHED, parts, geoParts, jobMerged, jobRefused, draws, geometries, (double) used * STRIDE / 1048576.0, fallbacks, residentDraws, residentUploads,
                (double) RES_ARENA.capacity * RES_ARENA.stride / 1048576.0, pUsed - pFreeN, persistDraws, persistUploads, litParts, litRefused,
                LightBoxes.report());
    }
}
