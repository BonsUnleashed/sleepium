package dev.sleep.shaders;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

/**
 * A pack's framebuffer: colortex0..15 as two textures each (main / alt: a composite-style pass reads main, writes alt,
 * then the written buffers flip), depthtex0 (the depth every gbuffers draw writes), depthtex1 (copied before the
 * translucents), depthtex2 (copied before the hand); formats, clears and mipmaps from the pack's const directives.
 * Framebuffers are made per set of draw buffers (and main / alt choice) and kept.
 *
 * <p>Texture units: every pack sampler has one fixed unit ({@link #unit}), from 12 up - vanilla's Sampler0..11 keep
 * 0..11 and GlStateManager's tracking stays right.
 */
public final class RenderTargets {
    public static final int COLOR_TEX = 16;
    public int width, height;
    /** per colortex: {textureA, textureB}, 0 = not made (the pack never uses it) */
    final int[][] tex = new int[COLOR_TEX][2];
    /** per colortex: which of the two is main now (0 / 1) */
    final int[] mainIndex = new int[COLOR_TEX];
    final int[] internalFormat = new int[COLOR_TEX];
    final boolean[] clear = new boolean[COLOR_TEX];
    /** -Dsleep.shaders.graphPoison: the clears FrameGraph skips fill the texture with 1000 magenta instead (any read shows) */
    private static final boolean POISON = Boolean.getBoolean("sleep.shaders.graphPoison");
    private static final float[] POISON_COLOR = {1000, 0, 1000, 1};
    /** FrameGraph: which role of each buffer the frame start clears (null: both) */
    boolean[][] clearRole;
    final float[][] clearColor = new float[COLOR_TEX][];
    final boolean[] mipmap = new boolean[COLOR_TEX];
    public final boolean[] used = new boolean[COLOR_TEX];
    public int depthTex0, depthTex1, depthTex2;
    private int depthFbo;
    /** -Dsleep.shaders.texClearDepth=true: depth cleared on the texture (the old way; an early-Z probe) */
    private static final boolean TEX_CLEAR_DEPTH = Boolean.getBoolean("sleep.shaders.texClearDepth");
    private final Map<String, Integer> fbos = new HashMap<>();

    /** sampler name -> fixed texture unit */
    public static final Map<String, Integer> UNITS = new HashMap<>();

    static {
        for (int i = 0; i < COLOR_TEX; i++) UNITS.put("colortex" + i, 12 + i);
        String[] legacy = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < legacy.length; i++) UNITS.put(legacy[i], 12 + (i < 4 ? i : 4 + (i - 4)));
        UNITS.put("depthtex0", 28);
        UNITS.put("gdepthtex", 28);
        UNITS.put("depthtex1", 29);
        UNITS.put("depthtex2", 30);
        UNITS.put("shadowtex0", 31);
        UNITS.put("watershadow", 31);
        UNITS.put("shadow", 31);
        UNITS.put("shadowtex1", 32);
        UNITS.put("shadowcolor", 33);
        UNITS.put("shadowcolor0", 33);
        UNITS.put("shadowcolor1", 34);
        UNITS.put("noisetex", 35);
        UNITS.put("normals", 36);
        UNITS.put("specular", 37);
    }

    public static int unit(String sampler) {
        Integer u = UNITS.get(sampler);
        return u == null ? -1 : u;
    }

    /** colortex index of a sampler name (colortexN or a legacy alias), or -1 */
    public static int colorIndex(String sampler) {
        if (sampler.startsWith("colortex")) {
            try {
                return Integer.parseInt(sampler.substring(8));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return switch (sampler) {
            case "gcolor" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "composite" -> 3;
            case "gaux1" -> 4;
            case "gaux2" -> 5;
            case "gaux3" -> 6;
            case "gaux4" -> 7;
            default -> -1;
        };
    }

    /** the pack's const directives (formats, clears, clear colours, mipmaps) */
    public void configure(Map<String, String> consts, boolean[] usedBuffers) {
        String[] legacy = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < COLOR_TEX; i++) {
            String n = "colortex" + i, alias = i < 8 ? legacy[i] : null;
            String f = consts.getOrDefault(n + "Format", alias == null ? null : consts.get(alias + "Format"));
            internalFormat[i] = f == null ? GL11.GL_RGBA8 : format(f);
            if (i == 1 && f == null && usedBuffers[1]) internalFormat[i] = GL30.GL_RGBA32F;   // gdepth default (OptiFine)
            String c = consts.getOrDefault(n + "Clear", alias == null ? null : consts.get(alias + "Clear"));
            clear[i] = c == null || !c.equals("false");
            String cc = consts.getOrDefault(n + "ClearColor", alias == null ? null : consts.get(alias + "ClearColor"));
            clearColor[i] = cc == null ? null : vec4(cc);
            String mm = consts.getOrDefault(n + "MipmapEnabled", alias == null ? null : consts.get(alias + "MipmapEnabled"));
            mipmap[i] = "true".equals(mm);
            used[i] = usedBuffers[i];
        }
    }

    /** (re)makes every texture at this size */
    public void resize(int w, int h) {
        if (w == width && h == height && depthTex0 != 0) return;
        delete();
        width = w;
        height = h;
        for (int i = 0; i < COLOR_TEX; i++) {
            if (!used[i]) continue;
            for (int k = 0; k < 2; k++) tex[i][k] = makeTexture(internalFormat[i], w, h, mipmap[i]);
            mainIndex[i] = 0;
        }
        depthTex0 = makeDepth(w, h);
        depthTex1 = makeDepth(w, h);
        depthTex2 = makeDepth(w, h);
    }

    public int main(int i) {
        return tex[i][mainIndex[i]];
    }

    public int alt(int i) {
        return tex[i][1 - mainIndex[i]];
    }

    public void flip(int i) {
        mainIndex[i] = 1 - mainIndex[i];
    }

    /**
     * The framebuffer writing these colortex (in this order: output i -> attachment i) and depthtex0 when depth.
     * alt: the alt textures (composite-style passes), else main.
     */
    public int framebuffer(int[] buffers, boolean alt, boolean depth) {
        // by the draw-buffer array (a program's own) and which texture of each buffer: no key built per call
        long state = (alt ? 1L : 0) | (depth ? 2L : 0);
        for (int b : buffers) if (b < COLOR_TEX && mainIndex[b] == 1) state |= 4L << b;
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap byState = FAST.get(buffers);
        if (byState == null) FAST.put(buffers, byState = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap());
        int fb = byState.getOrDefault(state, 0);
        if (fb != 0) return fb;
        fb = framebuffer0(buffers, alt, depth);
        byState.put(state, fb);
        return fb;
    }

    private final java.util.IdentityHashMap<int[], it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap> FAST = new java.util.IdentityHashMap<>();

    private int framebuffer0(int[] buffers, boolean alt, boolean depth) {
        StringBuilder k = new StringBuilder(alt ? "a" : "m").append(depth ? "d" : "-");
        for (int b : buffers) k.append(',').append(b).append(':').append(alt ? alt(b) : main(b));
        Integer id = fbos.get(k.toString());
        if (id != null) return id;
        int fb = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fb);
        int[] att = new int[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            int t = alt ? alt(buffers[i]) : main(buffers[i]);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, t, 0);
            att[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        if (depth) GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex0, 0);
        GL30.glDrawBuffers(att);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) System.out.println("[sleep/shaders] framebuffer " + k + " incomplete: 0x" + Integer.toHexString(status));
        fbos.put(k.toString(), fb);
        return fb;
    }

    /**
     * The start of a frame: every buffer with Clear on, both textures, to its clear colour; depthtex0 to 1. Straight on
     * the textures (glClearTexImage), no framebuffer made or re-attached each frame (that cost 0.45 ms of CPU a frame).
     */
    public void clearAll(float[] fogColor) {
        for (int i = 0; i < COLOR_TEX; i++) {
            if (!used[i] || !clear[i]) continue;
            float[] c = clearColor[i] != null ? clearColor[i] : i == 0 ? new float[] {fogColor[0], fogColor[1], fogColor[2], 1} : i == 1 ? new float[] {1, 1, 1, 1}
                    : new float[] {0, 0, 0, 0};
            // role 0: the texture main now, 1: the alt one (FrameGraph: a role written whole before any read is not cleared)
            for (int k = 0; k < 2; k++) {
                if (clearRole == null || clearRole[i][k]) clearTexture(k == 0 ? main(i) : alt(i), internalFormat[i], c);
                else if (POISON) clearTexture(k == 0 ? main(i) : alt(i), internalFormat[i], POISON_COLOR);   // a probe: a read of it shows
            }
        }
        if (TEX_CLEAR_DEPTH) org.lwjgl.opengl.GL44.glClearTexImage(depthTex0, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, new float[] {1});
        else {
            // a framebuffer clear: the driver's fast clear also resets the depth buffer's hierarchical-Z / compression
            // state; glClearTexImage left early rejection off for the frame (11-02, terrain with Complementary:
            // fragment shader invocations 3975K -> 2974K a frame, Iris + Sodium 2834K; GPU 0.97 -> 0.79 ms)
            if (depthFbo == 0) {
                depthFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex0, 0);
                GL11.glDrawBuffer(GL11.GL_NONE);
            } else GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo);
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    /** a colour texture's level 0 to c (integer formats get the values as integers) */
    static void clearTexture(int tex, int internalFormat, float[] c) {
        if (isInteger(internalFormat)) {
            int[] v = {(int) c[0], (int) c[1], (int) c[2], (int) c[3]};
            org.lwjgl.opengl.GL44.glClearTexImage(tex, 0, GL30.GL_RGBA_INTEGER, GL11.GL_INT, v);
        } else org.lwjgl.opengl.GL44.glClearTexImage(tex, 0, GL11.GL_RGBA, GL11.GL_FLOAT, c);
    }

    /** depthtex0 -> depthtex1 (before translucents) or depthtex2 (before the hand) */
    public void copyDepth(int to) {
        GL43.glCopyImageSubData(depthTex0, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, to, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
    }

    /** mipmaps of buffer i's main texture (before a pass that wants them) */
    public void generateMipmap(int i) {
        if (!used[i]) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, main(i));
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }

    public boolean mipmapped(int i) {
        return mipmap[i];
    }

    public void delete() {
        FAST.clear();
        if (depthFbo != 0) {
            GL30.glDeleteFramebuffers(depthFbo);
            depthFbo = 0;
        }
        for (int id : fbos.values()) GL30.glDeleteFramebuffers(id);
        fbos.clear();
        for (int i = 0; i < COLOR_TEX; i++)
            for (int k = 0; k < 2; k++)
                if (tex[i][k] != 0) {
                    GL11.glDeleteTextures(tex[i][k]);
                    tex[i][k] = 0;
                }
        for (int d : new int[] {depthTex0, depthTex1, depthTex2}) if (d != 0) GL11.glDeleteTextures(d);
        depthTex0 = depthTex1 = depthTex2 = 0;
    }

    private static int makeTexture(int internal, int w, int h, boolean mip) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        int levels = mip ? 1 + (int) Math.floor(Math.log(Math.max(w, h)) / Math.log(2)) : 1;
        GL43.glTexStorage2D(GL11.GL_TEXTURE_2D, levels, internal, w, h);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mip ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (isInteger(internal)) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        return t;
    }

    private static int makeDepth(int w, int h) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL43.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_DEPTH_COMPONENT32F, w, h);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        return t;
    }

    static boolean isInteger(int f) {
        return f >= GL30.GL_RGBA32UI && f <= GL30.GL_RGB8I || f >= GL30.GL_R8I && f <= GL30.GL_RG32UI || f == 0x906F;
    }

    static float[] vec4(String s) {
        String in = s.replaceAll(".*\\(|\\).*", "");
        String[] p = in.split(",");
        float[] v = new float[4];
        for (int i = 0; i < 4 && i < p.length; i++) v[i] = Float.parseFloat(p[i].trim().replaceAll("[fF]$", ""));
        return v;
    }

    /** a pack format name -> GL internal format */
    static int format(String name) {
        return switch (name.trim()) {
            case "R8" -> GL30.GL_R8;
            case "RG8" -> GL30.GL_RG8;
            case "RGB8" -> GL11.GL_RGB8;
            case "RGBA8", "RGBA" -> GL11.GL_RGBA8;
            case "R8_SNORM" -> 0x8F94;
            case "RG8_SNORM" -> 0x8F95;
            case "RGB8_SNORM" -> 0x8F96;
            case "RGBA8_SNORM" -> 0x8F97;
            case "R16" -> GL30.GL_R16;
            case "RG16" -> GL30.GL_RG16;
            case "RGB16" -> GL11.GL_RGB16;
            case "RGBA16" -> GL11.GL_RGBA16;
            case "R16_SNORM" -> 0x8F98;
            case "RG16_SNORM" -> 0x8F99;
            case "RGB16_SNORM" -> 0x8F9A;
            case "RGBA16_SNORM" -> 0x8F9B;
            case "R16F" -> GL30.GL_R16F;
            case "RG16F" -> GL30.GL_RG16F;
            case "RGB16F" -> GL30.GL_RGB16F;
            case "RGBA16F" -> GL30.GL_RGBA16F;
            case "R32F" -> GL30.GL_R32F;
            case "RG32F" -> GL30.GL_RG32F;
            case "RGB32F" -> GL30.GL_RGB32F;
            case "RGBA32F" -> GL30.GL_RGBA32F;
            case "R8I" -> GL30.GL_R8I;
            case "RG8I" -> GL30.GL_RG8I;
            case "RGB8I" -> GL30.GL_RGB8I;
            case "RGBA8I" -> GL30.GL_RGBA8I;
            case "R8UI" -> GL30.GL_R8UI;
            case "RG8UI" -> GL30.GL_RG8UI;
            case "RGB8UI" -> GL30.GL_RGB8UI;
            case "RGBA8UI" -> GL30.GL_RGBA8UI;
            case "R16I" -> GL30.GL_R16I;
            case "RG16I" -> GL30.GL_RG16I;
            case "RGB16I" -> GL30.GL_RGB16I;
            case "RGBA16I" -> GL30.GL_RGBA16I;
            case "R16UI" -> GL30.GL_R16UI;
            case "RG16UI" -> GL30.GL_RG16UI;
            case "RGB16UI" -> GL30.GL_RGB16UI;
            case "RGBA16UI" -> GL30.GL_RGBA16UI;
            case "R32I" -> GL30.GL_R32I;
            case "RG32I" -> GL30.GL_RG32I;
            case "RGB32I" -> GL30.GL_RGB32I;
            case "RGBA32I" -> GL30.GL_RGBA32I;
            case "R32UI" -> GL30.GL_R32UI;
            case "RG32UI" -> GL30.GL_RG32UI;
            case "RGB32UI" -> GL30.GL_RGB32UI;
            case "RGBA32UI" -> GL30.GL_RGBA32UI;
            case "R3_G3_B2" -> GL11.GL_R3_G3_B2;
            case "RGB5_A1" -> GL11.GL_RGB5_A1;
            case "RGB10_A2" -> GL11.GL_RGB10_A2;
            case "RGB10_A2UI" -> 0x906F;
            case "R11F_G11F_B10F" -> GL30.GL_R11F_G11F_B10F;
            case "RGB9_E5" -> GL30.GL_RGB9_E5;
            case "RGBA4" -> GL11.GL_RGBA4;
            case "RGB565" -> 0x8D62;
            case "RGBA2" -> GL11.GL_RGBA2;
            default -> {
                System.out.println("[sleep/shaders] unknown buffer format " + name + ": RGBA8");
                yield GL11.GL_RGBA8;
            }
        };
    }
}
