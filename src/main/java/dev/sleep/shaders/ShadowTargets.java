package dev.sleep.shaders;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

/**
 * A pack's shadow map: shadowtex0 (every caster's depth), shadowtex1 (copied before the translucent casters),
 * shadowcolor0..1; square, shadowMapResolution. Hardware filtering (compare mode) and nearest filtering per the pack's
 * consts; shadowcolor formats / clears like the colour buffers.
 */
public final class ShadowTargets {
    public final int size;
    public final int depth0, depth1;
    public final int[] color = new int[2];
    final int[] formats = new int[2];
    final boolean[] clear = {true, true};
    final float[][] clearColor = new float[2][];
    private final Map<String, Integer> fbos = new HashMap<>();

    ShadowTargets(Map<String, String> consts) {
        size = (int) Float.parseFloat(consts.getOrDefault("shadowMapResolution", "1024").replaceAll("[fF]$", ""));
        boolean hw = "true".equals(consts.get("shadowHardwareFiltering"));
        depth0 = depth(size, hw || "true".equals(consts.get("shadowHardwareFiltering0")), nearest(consts, 0));
        depth1 = depth(size, hw || "true".equals(consts.get("shadowHardwareFiltering1")), nearest(consts, 1));
        for (int i = 0; i < 2; i++) {
            String f = consts.getOrDefault("shadowcolor" + i + "Format", i == 0 ? consts.get("shadowcolorFormat") : null);
            formats[i] = f == null ? GL11.GL_RGBA8 : RenderTargets.format(f);
            String c = consts.get("shadowcolor" + i + "Clear");
            clear[i] = c == null || !c.equals("false");
            String cc = consts.get("shadowcolor" + i + "ClearColor");
            clearColor[i] = cc == null ? new float[] {1, 1, 1, 1} : RenderTargets.vec4(cc);
            int t = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
            GL43.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, formats[i], size, size);
            boolean near = "true".equals(consts.get("shadowcolor" + i + "Nearest")) || "true".equals(consts.get("shadowColor" + i + "Nearest"));
            int filter = near || RenderTargets.isInteger(formats[i]) ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            color[i] = t;
        }
    }

    private static boolean nearest(Map<String, String> consts, int i) {
        return "true".equals(consts.get("shadowtexNearest")) || "true".equals(consts.get("shadowtex" + i + "Nearest"))
                || "true".equals(consts.get("shadow" + i + "MinMagNearest"));
    }

    private static int depth(int size, boolean compare, boolean nearest) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL43.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_DEPTH_COMPONENT32F, size, size);
        int filter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, compare ? GL30.GL_COMPARE_REF_TO_TEXTURE : GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        return t;
    }

    /** the shadow framebuffer writing these shadowcolor buffers (output i -> attachment i) and shadowtex0 */
    public int framebuffer(int[] buffers) {
        StringBuilder k = new StringBuilder();
        for (int b : buffers) k.append(b).append(',');
        Integer id = fbos.get(k.toString());
        if (id != null) return id;
        int fb = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fb);
        int[] att = new int[buffers.length];
        int n = 0;
        for (int b : buffers) {
            if (b < 0 || b > 1) continue;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + n, GL11.GL_TEXTURE_2D, color[b], 0);
            att[n] = GL30.GL_COLOR_ATTACHMENT0 + n;
            n++;
        }
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth0, 0);
        if (n == 0) GL11.glDrawBuffer(GL11.GL_NONE);
        else GL30.glDrawBuffers(java.util.Arrays.copyOf(att, n));
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) System.out.println("[sleep/shaders] shadow framebuffer " + k + " incomplete: 0x" + Integer.toHexString(status));
        fbos.put(k.toString(), fb);
        return fb;
    }

    /** the start of the shadow pass: depth 1, shadowcolor to their clear colours (on the textures, no framebuffer) */
    public void clear() {
        for (int i = 0; i < 2; i++) if (clear[i]) RenderTargets.clearTexture(color[i], formats[i], clearColor[i]);
        // depth through a framebuffer clear (the fast clear resets hierarchical-Z; a texture clear left early
        // rejection off for the frame: 40 % more fragment shading on the camera's terrain, measured 11-02)
        if (depthFbo == 0) {
            depthFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth0, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
        } else GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthFbo);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    private int depthFbo;

    /** shadowtex0 -> shadowtex1 (before the translucent casters) */
    public void copyOpaque() {
        GL43.glCopyImageSubData(depth0, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, depth1, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, size, size, 1);
    }
}
