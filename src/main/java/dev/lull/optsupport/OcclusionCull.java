package dev.lull.optsupport;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.system.MemoryStack;

/**
 * GPU occlusion culling for the camera's chunk multi-draws (notes/52): a layer's draws go in two phases.
 * <ol>
 * <li>A compute pass keeps the draws of sections that were visible last frame; they are drawn.</li>
 * <li>The depth they left (with everything drawn before) becomes a hierarchical-Z pyramid (max depth per texel, from
 * half resolution); a second pass tests every draw's section box (camera-relative, 2 blocks of margin for model
 * elements past the section and vertex animation, 1 pixel for jitter) against it, records the section's visibility for
 * the next frame, and keeps those visible now that phase 1 did not draw; they are drawn.</li>
 * </ol>
 * Every section visible this frame is drawn in one of the phases (phase 2 tests against depth of geometry actually
 * drawn), and the draws keep their order: a phase's commands are the layer's commands with the instance count 0 for
 * the ones it does not draw (compacting them with an atomic counter reorders them, and coplanar faces of different
 * draws then changed pixels: 19 in a frame, 11-02). A still camera draws everything in phase 1, in the original order:
 * the same frame. Buffers the draws read are immutable (Intel refuses indirect draws from re-specified ones, see
 * IndirectCommands), used as a ring. -Dlab.chunkmd.occlusion=true.
 */
public final class OcclusionCull {
    public static final boolean ON = Boolean.getBoolean("lab.chunkmd.occlusion");
    private static final boolean STATS = Boolean.getBoolean("lab.chunkmd.occlusion.stats");
    /** -Dlab.chunkmd.occlusion.everyLayer: a pyramid per layer (later layers tested against earlier ones' depth too) */
    private static final boolean EVERY_LAYER = Boolean.getBoolean("lab.chunkmd.occlusion.everyLayer");
    /** -Dlab.chunkmd.occlusion.noTest: every box visible (a probe: the machinery without culling) */
    private static final boolean NO_TEST = Boolean.getBoolean("lab.chunkmd.occlusion.noTest");
    private static final int B_IN = 10, B_OFFS = 11, B_SLOTS = 12, B_VIS_PREV = 13, B_VIS_NEXT = 14, B_OUT = 15, B_STATS = 16, B_COUNT = 17;
    /** a texture unit no program of ours or a pack's uses (the pack's reach ~40) */
    private static final int UNIT = 63;
    private static int downProg, cullProg, sampler, depthSampler;
    private static int uCountIndex, uN, uInBase, uOffBase, uSlotBase, uOutBase, uPhase, uLevels, uViewProj, uHizSize, uHiz, uStats;
    private static int uDownSrcLevel, uDownSrcSize, uDownDstSize, uDownSrc;
    private static int hiz, hizW, hizH, hizLevels;
    private static long hizFrame = -1;
    private static final int[] vis = new int[2];
    private static int visCap, visCur, layersSeen;
    private static int slotsBuf, statsBuf;
    private static int outBuf;
    private static long outCap, outCursor, outRegion;
    private static int regionCmds;
    private static boolean broken, fresh, checked;
    /** indirect draw counts (GL 4.6 / ARB_indirect_parameters): a phase with nothing to draw walks no commands */
    static boolean countDraws, gl46;
    private static int countBuf, countCursor, countRegion, arenas;
    private static final int COUNT_CAP = 1 << 16;
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    public static long drawsIn, drawsPhase1, drawsPhase2, frames;

    private OcclusionCull() {}

    /** the camera's matrices of the layer about to be drawn (ChunkMultiDrawMixin) */
    public static void matrices(Matrix4f modelView, Matrix4f projection) {
        VIEW_PROJ.set(projection).mul(modelView);
        fresh = true;
    }

    /** on, and the layer's matrices given (a path that does not give them draws unculled) */
    static boolean usable() {
        return ON && !broken && fresh;
    }

    // ------------------------------------------------------------------ entry ids (visibility slots)

    private static int nextId;
    private static final it.unimi.dsi.fastutil.ints.IntArrayList FREE = new it.unimi.dsi.fastutil.ints.IntArrayList();

    static int newId() {
        return FREE.isEmpty() ? nextId++ : FREE.popInt();
    }

    static void freeId(int id) {
        if (id >= 0) FREE.add(id);
    }

    // ------------------------------------------------------------------ programs

    private static final String DOWN = """
            #version 430
            layout(local_size_x = 8, local_size_y = 8) in;
            uniform sampler2D src;
            uniform int srcLevel;
            uniform ivec2 srcSize, dstSize;
            layout(r32f, binding = 0) uniform writeonly image2D dst;
            void main() {
                ivec2 p = ivec2(gl_GlobalInvocationID.xy);
                if (p.x >= dstSize.x || p.y >= dstSize.y) return;
                ivec2 s = p * 2;
                // an odd source edge folds into the last texel: texel p of level l covers pixels [p, p + 1) * 2^(l+1), the last one to the edge
                int ex = (p.x == dstSize.x - 1 && (srcSize.x & 1) == 1) ? 2 : 1;
                int ey = (p.y == dstSize.y - 1 && (srcSize.y & 1) == 1) ? 2 : 1;
                float m = 0.0;
                for (int y = 0; y <= ey; y++)
                    for (int x = 0; x <= ex; x++) m = max(m, texelFetch(src, min(s + ivec2(x, y), srcSize - 1), srcLevel).r);
                imageStore(dst, p, vec4(m));
            }
            """;

    // (no readonly / writeonly on the storage blocks: Intel's GL compiler mishandles them, see the N100 notes)
    private static final String CULL = """
            #version 430
            layout(local_size_x = 64) in;
            layout(std430, binding = 10) buffer InCmds { uint inW[]; };
            layout(std430, binding = 11) buffer Offs { vec4 offs[]; };
            layout(std430, binding = 12) buffer Slots { uint slots[]; };
            layout(std430, binding = 13) buffer VisPrev { uint visPrev[]; };
            layout(std430, binding = 14) buffer VisNext { uint visNext[]; };
            layout(std430, binding = 15) buffer OutCmds { uint outW[]; };
            layout(std430, binding = 16) buffer Stats { uint stats[]; };
            layout(std430, binding = 17) buffer Counts { uint counts[]; };
            uniform uint n, inBase, offBase, slotBase, outBase, countIndex;
            uniform int phase, levels, noTest, doStats;
            uniform mat4 viewProj;
            uniform sampler2D hiz;
            uniform vec2 hizSize;   // the depth's size in pixels (level 0 is half of it)

            bool visible(vec3 o) {
                if (noTest != 0) return true;
                vec3 mn = o - 2.0, mx = o + 18.0;
                vec2 smin = vec2(1e9), smax = vec2(-1e9);
                float zmin = 1e9;
                for (int k = 0; k < 8; k++) {
                    vec3 c = vec3((k & 1) != 0 ? mx.x : mn.x, (k & 2) != 0 ? mx.y : mn.y, (k & 4) != 0 ? mx.z : mn.z);
                    vec4 p = viewProj * vec4(c, 1.0);
                    if (p.w <= 0.05) return true;   // reaches the near plane (or the camera is inside): no screen bounds
                    vec3 ndc = p.xyz / p.w;
                    smin = min(smin, ndc.xy);
                    smax = max(smax, ndc.xy);
                    zmin = min(zmin, ndc.z);
                }
                if (smax.x < -1.0 || smax.y < -1.0 || smin.x > 1.0 || smin.y > 1.0) return false;
                float z = zmin * 0.5 + 0.5;
                // pixels, 1 of margin (a pack's temporal jitter), then the level where the box spans about 2 texels
                vec2 a = clamp((smin * 0.5 + 0.5) * hizSize - 1.0, vec2(0.0), hizSize);
                vec2 b = clamp((smax * 0.5 + 0.5) * hizSize + 1.0, vec2(0.0), hizSize);
                vec2 px = b - a;
                int level = clamp(int(ceil(log2(max(max(px.x, px.y), 1.0)))) - 1, 0, levels - 1);
                ivec2 ls = textureSize(hiz, level);
                // pixel -> texel of this level (folded odd edges: the computed index is the true one or one less: +-1 texel)
                vec2 sc = vec2(ls) / hizSize;
                ivec2 ta = clamp(ivec2(floor(a * sc)) - 1, ivec2(0), ls - 1);
                ivec2 tb = clamp(ivec2(floor(b * sc)) + 1, ivec2(0), ls - 1);
                for (int y = ta.y; y <= tb.y; y++)
                    for (int x = ta.x; x <= tb.x; x++)
                        if (texelFetch(hiz, ivec2(x, y), level).r >= z) return true;
                return false;
            }

            void main() {
                uint i = gl_GlobalInvocationID.x;
                if (i >= n) return;
                uint w = inBase + i * 5u;
                uint slot = slots[slotBase + i];
                bool last = visPrev[slot] != 0u;
                bool emit;
                if (phase == 1) emit = last;
                else {
                    bool v = visible(offs[offBase + inW[w + 4u]].xyz);
                    visNext[slot] = v ? 1u : 0u;
                    emit = v && !last;
                }
                uint o = (outBase + i) * 5u;
                outW[o] = inW[w];
                outW[o + 1u] = emit ? 1u : 0u;
                outW[o + 2u] = inW[w + 2u];
                outW[o + 3u] = inW[w + 3u];
                outW[o + 4u] = inW[w + 4u];
                if (doStats != 0 && emit) atomicAdd(stats[phase - 1], 1u);
                // the phase's draw count: all its commands when one draws (the same value from every thread), else 0
                if (emit) counts[countIndex] = n;
            }
            """;

    private static int program(String src) {
        int sh = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        GL20C.glShaderSource(sh, src);
        GL20C.glCompileShader(sh);
        if (GL20C.glGetShaderi(sh, GL20C.GL_COMPILE_STATUS) == 0) {
            System.out.println("[sleep/opt] occlusion culling: " + GL20C.glGetShaderInfoLog(sh));
            broken = true;
            return 0;
        }
        int p = GL20C.glCreateProgram();
        GL20C.glAttachShader(p, sh);
        GL20C.glLinkProgram(p);
        GL20C.glDeleteShader(sh);
        if (GL20C.glGetProgrami(p, GL20C.GL_LINK_STATUS) == 0) {
            System.out.println("[sleep/opt] occlusion culling: " + GL20C.glGetProgramInfoLog(p));
            broken = true;
            return 0;
        }
        return p;
    }

    private static boolean init() {
        if (cullProg != 0) return true;
        if (broken) return false;
        var caps = GL.getCapabilities();
        if (!(caps.OpenGL44 || caps.GL_ARB_buffer_storage) || GL11C.glGetInteger(GL43C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) <= B_COUNT
                || GL11C.glGetInteger(GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) <= UNIT) {
            System.out.println("[sleep/opt] occlusion culling: off (needs buffer storage, 18 storage bindings, 64 texture units)");
            broken = true;
            return false;
        }
        downProg = program(DOWN);
        cullProg = program(CULL);
        if (broken) return false;
        uN = GL20C.glGetUniformLocation(cullProg, "n");
        uInBase = GL20C.glGetUniformLocation(cullProg, "inBase");
        uOffBase = GL20C.glGetUniformLocation(cullProg, "offBase");
        uSlotBase = GL20C.glGetUniformLocation(cullProg, "slotBase");
        uOutBase = GL20C.glGetUniformLocation(cullProg, "outBase");
        uPhase = GL20C.glGetUniformLocation(cullProg, "phase");
        uCountIndex = GL20C.glGetUniformLocation(cullProg, "countIndex");
        gl46 = caps.OpenGL46;
        countDraws = (caps.OpenGL46 || caps.GL_ARB_indirect_parameters) && !Boolean.getBoolean("lab.chunkmd.occlusion.noCount");
        uLevels = GL20C.glGetUniformLocation(cullProg, "levels");
        uViewProj = GL20C.glGetUniformLocation(cullProg, "viewProj");
        uHizSize = GL20C.glGetUniformLocation(cullProg, "hizSize");
        uHiz = GL20C.glGetUniformLocation(cullProg, "hiz");
        uStats = GL20C.glGetUniformLocation(cullProg, "doStats");
        GL20C.glUseProgram(cullProg);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(cullProg, "noTest"), NO_TEST ? 1 : 0);
        GL20C.glUniform1i(uStats, STATS ? 1 : 0);
        GL20C.glUniform1i(uHiz, UNIT);
        uDownSrcLevel = GL20C.glGetUniformLocation(downProg, "srcLevel");
        uDownSrcSize = GL20C.glGetUniformLocation(downProg, "srcSize");
        uDownDstSize = GL20C.glGetUniformLocation(downProg, "dstSize");
        uDownSrc = GL20C.glGetUniformLocation(downProg, "src");
        GL20C.glUseProgram(downProg);
        GL20C.glUniform1i(uDownSrc, UNIT);
        GL20C.glUseProgram(0);
        // read with texelFetch: no comparison mode (a depth texture may have one), nearest; the depth has one level
        // (a mipmap filter would make it incomplete: texelFetch then returns 0)
        sampler = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(sampler, GL30C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST_MIPMAP_NEAREST);
        GL33C.glSamplerParameteri(sampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        depthSampler = GL33C.glGenSamplers();
        GL33C.glSamplerParameteri(depthSampler, GL30C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        GL33C.glSamplerParameteri(depthSampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(depthSampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        slotsBuf = GL15C.glGenBuffers();
        statsBuf = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, statsBuf);
        GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 16, GL15C.GL_DYNAMIC_READ);   // [phase 1, phase 2, a count sink]
        System.out.println("[sleep/opt] occlusion culling: on (two phases, hierarchical Z" + (EVERY_LAYER ? " per layer" : " per frame") + ")");
        return true;
    }

    private static void ensureVis(int slots) {
        if (slots <= visCap) return;
        int cap = Math.max(slots, Math.max(4096, visCap * 2));
        for (int k = 0; k < 2; k++) {
            int b = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, b);
            GL15C.glBufferData(GL31C.GL_COPY_WRITE_BUFFER, 4L * cap, GL15C.GL_DYNAMIC_DRAW);
            if (vis[k] != 0) {
                GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, vis[k]);
                GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, 0, 0, 4L * visCap);
                GL15C.glDeleteBuffers(vis[k]);
            }
            // a new slot: not visible last frame (it is tested in phase 2)
            GL43C.glClearBufferSubData(GL31C.GL_COPY_WRITE_BUFFER, GL30C.GL_R32UI, 4L * visCap, 4L * (cap - visCap), GL30C.GL_RED_INTEGER, GL11C.GL_UNSIGNED_INT,
                    (java.nio.IntBuffer) null);
            vis[k] = b;
        }
        visCap = cap;
    }

    // ------------------------------------------------------------------ a layer's draws

    /**
     * A layer's culling starts: {@code layer} (index; a layer seen twice means a new frame - visibility flips), the
     * slots of its {@code cmds} commands (an int each, parallel to them). Reserves the output (both phases).
     */
    static boolean begin(int layer, long slotsAddress, int cmds, int arenaCount) {
        fresh = false;
        if (!init()) return false;
        if ((layersSeen & (1 << layer)) != 0) {
            layersSeen = 0;
            visCur ^= 1;
            frames++;
            if (STATS && frames % 300 == 0) {
                System.out.printf("[sleep/opt] occlusion culling: %d draws/frame tested, phase 1 drew %d, phase 2 %d%n", drawsIn / 300, drawsPhase1 / 300,
                        drawsPhase2 / 300);
                drawsIn = drawsPhase1 = drawsPhase2 = 0;
            }
        }
        layersSeen |= 1 << layer;
        ensureVis(nextId + 1);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, slotsBuf);
        GL15C.nglBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 4L * cmds, slotsAddress, GL15C.GL_STREAM_DRAW);
        long outBytes = 20L * 2 * cmds;
        if (outBytes > outCap / 4) {
            // room for many layers' output: a region rewritten while the GPU may still read it would wait
            outCap = Math.max(1L << 20, Long.highestOneBit(outBytes) << 3);
            if (outBuf != 0) GL15C.glDeleteBuffers(outBuf);
            outBuf = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, outBuf);
            GL44C.glBufferStorage(GL31C.GL_COPY_WRITE_BUFFER, outCap, 0);
            outCursor = 0;
        }
        if (outCursor + outBytes > outCap) outCursor = 0;
        outRegion = outCursor;
        outCursor = (outCursor + outBytes + 319) / 320 * 320;   // a whole number of commands (20 bytes), 64-aligned
        regionCmds = cmds;
        if (countDraws) {
            arenas = arenaCount;
            int bytes = 8 * arenaCount;
            if (countBuf == 0) {
                countBuf = GL15C.glGenBuffers();
                GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, countBuf);
                GL44C.glBufferStorage(GL31C.GL_COPY_WRITE_BUFFER, COUNT_CAP, 0);
            }
            if (countCursor + bytes > COUNT_CAP) countCursor = 0;
            countRegion = countCursor;
            countCursor = (countCursor + bytes + 63) & ~63;
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, countBuf);
            GL43C.glClearBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, GL30C.GL_R32UI, countRegion, bytes, GL30C.GL_RED_INTEGER, GL11C.GL_UNSIGNED_INT,
                    (java.nio.IntBuffer) null);
        }
        if (STATS) {
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, statsBuf);
            GL43C.glClearBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, GL30C.GL_R32UI, GL30C.GL_RED_INTEGER, GL11C.GL_UNSIGNED_INT, (java.nio.IntBuffer) null);
        }
        drawsIn += cmds;
        return true;
    }

    /** phase 2 needs a pyramid: per frame (built after the first layer's phase 1) or per layer */
    static boolean needHiZ() {
        return EVERY_LAYER || hizFrame != frames;
    }

    /**
     * One arena's phase: its {@code n} commands (the command buffer at cmdOffset bytes), offsets (offsBuffer at
     * offsOffset bytes) and slots (from firstCmd, the index of its first command in the layer) into the output.
     */
    static void cull(int phase, int arena, int cmdBuffer, long cmdOffset, int n, int offsBuffer, long offsOffset, int firstCmd) {
        GL20C.glUseProgram(cullProg);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_IN, cmdBuffer);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_OFFS, offsBuffer);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_SLOTS, slotsBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_VIS_PREV, vis[visCur ^ 1]);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_VIS_NEXT, vis[visCur]);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_OUT, outBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_STATS, statsBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, B_COUNT, countDraws ? countBuf : statsBuf);
        GL30C.glUniform1ui(uCountIndex, countDraws ? countRegion / 4 + 2 * arena + phase - 1 : 2);
        GL30C.glUniform1ui(uN, n);
        GL30C.glUniform1ui(uInBase, (int) (cmdOffset / 4));
        GL30C.glUniform1ui(uOffBase, (int) (offsOffset / 16));
        GL30C.glUniform1ui(uSlotBase, firstCmd);
        GL30C.glUniform1ui(uOutBase, (int) (outRegion / 20) + (phase == 2 ? regionCmds : 0) + firstCmd);
        GL20C.glUniform1i(uPhase, phase);
        int active = 0;
        if (phase == 2) {
            active = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, hiz);
            GL33C.glBindSampler(UNIT, sampler);
            GL20C.glUniform1i(uLevels, hizLevels);
            GL20C.glUniform2f(uHizSize, hizW, hizH);
            try (MemoryStack st = MemoryStack.stackPush()) {
                GL20C.glUniformMatrix4fv(uViewProj, false, VIEW_PROJ.get(st.mallocFloat(16)));
            }
        }
        GL43C.glDispatchCompute((n + 63) / 64, 1, 1);
        if (phase == 2) {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
            GL33C.glBindSampler(UNIT, 0);
            GL13C.glActiveTexture(active);
        }
    }

    /** after a phase's culling: the caller's program back, the output visible as commands, bound as them */
    static void barrierForDraws(int restoreProgram) {
        GL20C.glUseProgram(restoreProgram);
        GL42C.glMemoryBarrier(GL42C.GL_COMMAND_BARRIER_BIT);
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, outBuf);
        if (countDraws) GL15C.glBindBuffer(org.lwjgl.opengl.GL46C.GL_PARAMETER_BUFFER, countBuf);
    }

    /** a phase's draws of an arena (barrierForDraws bound the buffers): its commands, all or (counted) none */
    static void draw(int phase, int arena, int indexType, int n, int firstCmd) {
        long at = commands(phase, firstCmd);
        if (!countDraws) GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, indexType, at, n, 20);
        else {
            long count = countRegion + 4L * (2 * arena + phase - 1);
            if (gl46) org.lwjgl.opengl.GL46C.glMultiDrawElementsIndirectCount(GL11C.GL_TRIANGLES, indexType, at, count, n, 20);
            else org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(GL11C.GL_TRIANGLES, indexType, at, count, n, 20);
        }
    }

    static void endLayer() {
        if (countDraws) GL15C.glBindBuffer(org.lwjgl.opengl.GL46C.GL_PARAMETER_BUFFER, 0);
    }

    /** the byte offset in the bound command buffer of a phase's commands from the layer's command firstCmd */
    static long commands(int phase, int firstCmd) {
        return outRegion + 20L * ((phase == 2 ? regionCmds : 0) + firstCmd);
    }

    /** -Dlab.chunkmd.occlusion.stats: how many each phase drew (reads the counts back: a stall, statistics only) */
    static void stats() {
        if (!STATS) return;
        GL42C.glMemoryBarrier(GL42C.GL_BUFFER_UPDATE_BARRIER_BIT);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, statsBuf);
        try (MemoryStack st = MemoryStack.stackPush()) {
            var b = st.mallocInt(2);
            GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, b);
            drawsPhase1 += b.get(0);
            drawsPhase2 += b.get(1);
        }
    }

    /** the hierarchical-Z pyramid of the depth attached to the bound draw framebuffer, from half its resolution */
    static void buildHiZ(int restoreProgram) {
        hizFrame = frames;
        // the depth the layer draws into, known to the engine: GL queries of it (texture size, framebuffer attachment)
        // cost the CPU ~0.28 ms a frame (a driver sync while it is being drawn into, 11-02)
        // (1.21.1 took a shaderpack's depthtex0 from ShaderPipeline first: the pipeline is not on 1.20.1 yet)
        int depth, w, h;
        var rt = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        depth = rt.getDepthTextureId();
        w = rt.width;
        h = rt.height;
        if (!checked) {   // once: the bound framebuffer's depth is that texture
            checked = true;
            int type = GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int name = type == GL11C.GL_TEXTURE
                    ? GL30C.glGetFramebufferAttachmentParameteri(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
                    : 0;
            if (name != depth) {
                System.out.println("[sleep/opt] occlusion culling: off (the terrain's depth is not the expected texture: " + name + " vs " + depth + ")");
                broken = true;
                return;
            }
        }
        int active = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + UNIT);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depth);
        if (w != hizW || h != hizH || hiz == 0) {
            if (hiz != 0) GL11C.glDeleteTextures(hiz);
            hizW = w;
            hizH = h;
            // levels from half resolution down to about 8 texels: a box larger than the top level's texel scans it whole
            int lw = Math.max(1, w / 2), lh = Math.max(1, h / 2);
            hizLevels = 1;
            while (Math.max(lw, lh) > 8) {
                lw = Math.max(1, lw / 2);
                lh = Math.max(1, lh / 2);
                hizLevels++;
            }
            hiz = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, hiz);
            GL42C.glTexStorage2D(GL11C.GL_TEXTURE_2D, hizLevels, GL30C.GL_R32F, Math.max(1, w / 2), Math.max(1, h / 2));
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depth);
        }
        GL20C.glUseProgram(downProg);
        int sw = w, sh = h;
        for (int l = 0; l < hizLevels; l++) {
            int dw = Math.max(1, sw / 2), dh = Math.max(1, sh / 2);
            if (l == 1) {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, hiz);
                GL33C.glBindSampler(UNIT, sampler);
            } else if (l == 0) GL33C.glBindSampler(UNIT, depthSampler);
            if (l > 0) GL42C.glMemoryBarrier(GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42C.GL_TEXTURE_FETCH_BARRIER_BIT);
            GL20C.glUniform1i(uDownSrcLevel, Math.max(0, l - 1));
            GL20C.glUniform2i(uDownSrcSize, sw, sh);
            GL20C.glUniform2i(uDownDstSize, dw, dh);
            GL42C.glBindImageTexture(0, hiz, l, false, 0, GL15C.GL_WRITE_ONLY, GL30C.GL_R32F);
            GL43C.glDispatchCompute((dw + 7) / 8, (dh + 7) / 8, 1);
            sw = dw;
            sh = dh;
        }
        GL42C.glMemoryBarrier(GL42C.GL_TEXTURE_FETCH_BARRIER_BIT);
        GL42C.glBindImageTexture(0, 0, 0, false, 0, GL15C.GL_WRITE_ONLY, GL30C.GL_R32F);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        GL33C.glBindSampler(UNIT, 0);
        GL13C.glActiveTexture(active);
        GL20C.glUseProgram(restoreProgram);
    }
}
