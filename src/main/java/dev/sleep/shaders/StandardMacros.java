package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The macros every pack program sees right after #version (OptiFine's list as Iris 1.8.14 defines it). This engine
 * implements Iris 1.8.14's semantics (per-vertex entity / item ids, the render stages, the program set), so it defines
 * IS_IRIS and that version: packs pick their Iris paths. Feature flags (IRIS_FEATURE_*) only for what is implemented.
 */
public final class StandardMacros {
    /** WorldRenderingPhase order in Iris 1.8.14: MC_RENDER_STAGE_<name> = index */
    public static final String[] RENDER_STAGES = {"NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID", "TERRAIN_SOLID",
            "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT", "ENTITIES", "BLOCK_ENTITIES", "DESTROY", "OUTLINE", "DEBUG", "HAND_SOLID",
            "TERRAIN_TRANSLUCENT", "TRIPWIRE", "PARTICLES", "CLOUDS", "RAIN_SNOW", "WORLD_BORDER", "HAND_TRANSLUCENT"};

    private StandardMacros() {}

    /** render thread (GL strings); features: the IRIS_FEATURE_ flags this engine has */
    public static List<String> build(List<String> features) {
        List<String> m = new ArrayList<>();
        m.add("MC_VERSION 12101");
        int glMajor = GL11.glGetInteger(GL30.GL_MAJOR_VERSION), glMinor = GL11.glGetInteger(GL30.GL_MINOR_VERSION);
        m.add("MC_GL_VERSION " + (glMajor * 100 + glMinor * 10));
        String glsl = GL11.glGetString(0x8B8C);   // GL_SHADING_LANGUAGE_VERSION, "4.60 NVIDIA"
        int glslv = 330;
        try {
            String[] p = glsl.split("[ .]");
            glslv = Integer.parseInt(p[0]) * 100 + Integer.parseInt(p[1].substring(0, Math.min(2, p[1].length())));
        } catch (RuntimeException ignored) {}
        m.add("MC_GLSL_VERSION " + glslv);
        String os = System.getProperty("os.name", "").toLowerCase();
        m.add(os.contains("win") ? "MC_OS_WINDOWS" : os.contains("mac") ? "MC_OS_MAC" : os.contains("linux") ? "MC_OS_LINUX" : "MC_OS_UNKNOWN");
        String vendor = String.valueOf(GL11.glGetString(GL11.GL_VENDOR)).toLowerCase(), renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase();
        m.add(vendor.contains("ati") ? "MC_GL_VENDOR_ATI" : vendor.contains("amd") ? "MC_GL_VENDOR_AMD" : vendor.contains("intel") ? "MC_GL_VENDOR_INTEL"
                : vendor.contains("nvidia") ? "MC_GL_VENDOR_NVIDIA" : vendor.contains("x.org") ? "MC_GL_VENDOR_XORG" : "MC_GL_VENDOR_OTHER");
        m.add(renderer.contains("radeon") ? "MC_GL_RENDERER_RADEON" : renderer.contains("geforce") ? "MC_GL_RENDERER_GEFORCE"
                : renderer.contains("quadro") ? "MC_GL_RENDERER_QUADRO" : renderer.contains("intel") ? "MC_GL_RENDERER_INTEL"
                : renderer.contains("gallium") ? "MC_GL_RENDERER_GALLIUM" : renderer.contains("mesa") ? "MC_GL_RENDERER_MESA" : "MC_GL_RENDERER_OTHER");
        int n = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
        for (int i = 0; i < n; i++) {
            String e = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
            if (e != null && e.startsWith("GL_")) m.add("MC_" + e);
        }
        m.add("MC_NORMAL_MAP");
        m.add("MC_SPECULAR_MAP");
        m.add("MC_RENDER_QUALITY 1.0");
        m.add("MC_SHADOW_QUALITY 1.0");
        m.add("MC_HAND_DEPTH 0.125");
        m.add("MC_MIPMAP_LEVEL 4");
        for (int i = 0; i < RENDER_STAGES.length; i++) m.add("MC_RENDER_STAGE_" + RENDER_STAGES[i] + " " + i);
        m.add("IS_IRIS");
        m.add("IRIS_VERSION 10814");
        m.add("IRIS_TAG_SUPPORT 2");
        for (String f : features) m.add("IRIS_FEATURE_" + f);
        return m;
    }
}
