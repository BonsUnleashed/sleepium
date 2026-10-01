package dev.probe.optsupport;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;

/**
 * Material shading for part-batched geometry (PartBatch modes 1 and 3: vanilla parts and GeckoLib bones): a texture
 * {@code <texture>_s.png} beside the model's texture is a material mask in the LabPBR layout resource packs use -
 * R perceptual smoothness, G reflectance (230 and up: metal, tinted by the albedo), A emission (255: none). The
 * entity fragment shaders get: highlights from the two world lights, a sky reflection and a grazing rim for smooth
 * surfaces (metal tinted, dielectrics faint), all scaled by the lightmap (nothing shines in the dark), and emission
 * (full albedo whatever the light). A model without a mask draws exactly as before (the branch is off).
 * {@code -Dprobe.materials=false} turns it off.
 */
public final class Materials {
    private Materials() {}

    public static final boolean ON = !"false".equals(System.getProperty("probe.materials"));
    static final int UNIT = 7;   // a texture unit the entity shaders do not use
    /** the fragment stage reads what the vertex stage of the same name writes: patched only after it */
    private static final java.util.Set<String> VERTEX_PATCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static long bound, missing;

    private static boolean shaderTarget(String name) {
        return name.startsWith("rendertype_entity_cutout") || name.startsWith("rendertype_entity_translucent") || name.startsWith("rendertype_entity_solid")
                || name.equals("rendertype_armor_cutout_no_cull");
    }

    /** after PartBatch.patch: the fragment stage gets the posed normal and the camera-relative position */
    public static String patchVertex(String name, String src) {
        if (!ON || !shaderTarget(name) || !src.contains("probe_Normal")) return src;
        int end = src.lastIndexOf('}');
        int main = src.indexOf("void main() {");
        if (end < 0 || main < 0) return src;
        VERTEX_PATCHED.add(name);
        return src.substring(0, main) + "out vec3 probeMatN;\nout vec3 probeMatP;\n" + src.substring(main, end)
                + "    probeMatN = probe_Normal;\n    probeMatP = probe_Position;\n" + src.substring(end);
    }

    public static String patchFragment(String name, String src) {
        if (!ON || !VERTEX_PATCHED.contains(name) || !src.contains("texCoord0") || !src.contains("lightMapColor") || !src.contains("fragColor = linear_fog(")) return src;
        int main = src.indexOf("void main() {");
        if (main < 0) return src;
        String decl = "uniform sampler2D ProbeMaterial;\nuniform int ProbeMaterialOn;\nuniform vec3 Light0_Direction;\nuniform vec3 Light1_Direction;\n"
                + "in vec3 probeMatN;\nin vec3 probeMatP;\n\n";
        String shade = """
                    if (ProbeMaterialOn != 0) {
                        vec4 probe_m = texture(ProbeMaterial, texCoord0);
                        vec3 probe_alb = texture(Sampler0, texCoord0).rgb * vertexColor.a;
                        float probe_s = probe_m.r;
                        bool probe_metal = probe_m.g > 0.898;
                        float probe_f0 = probe_metal ? 1.0 : max(probe_m.g, 0.04);
                        float probe_e = probe_m.a < 0.999 ? probe_m.a : 0.0;
                        vec3 probe_N = normalize(probeMatN), probe_V = normalize(-probeMatP);
                        if (dot(probe_N, probe_V) < 0.0) probe_N = -probe_N;
                        float probe_lit = max(max(lightMapColor.r, lightMapColor.g), lightMapColor.b);
                        float probe_shin = mix(4.0, 40.0, probe_s * probe_s);   // broad: small flat faces must not flash on and off
                        vec3 probe_H0 = normalize(normalize(Light0_Direction) + probe_V), probe_H1 = normalize(normalize(Light1_Direction) + probe_V);
                        float probe_spec = (pow(max(dot(probe_N, probe_H0), 0.0), probe_shin) + 0.6 * pow(max(dot(probe_N, probe_H1), 0.0), probe_shin)) * (probe_shin + 8.0) / 60.0;
                        float probe_fres = pow(1.0 - max(dot(probe_N, probe_V), 0.0), 5.0);
                        vec3 probe_R = reflect(-probe_V, probe_N);
                        vec3 probe_sky = mix(vec3(0.13, 0.11, 0.10), mix(vec3(0.52, 0.55, 0.58), vec3(0.70, 0.78, 0.92), clamp(probe_R.y, 0.0, 1.0)), smoothstep(-0.3, 0.15, probe_R.y));
                        vec3 probe_tint = probe_metal ? probe_alb : vec3(1.0);
                        vec3 probe_add = probe_tint * probe_f0 * (probe_spec * probe_s * 1.6 + probe_sky * probe_s * (probe_metal ? 0.95 : 0.25))
                                + probe_sky * probe_fres * probe_s * (probe_metal ? 0.5 : 0.12);
                        if (probe_metal) color.rgb *= mix(1.0, 0.35, probe_s);   // metal: little diffuse, mostly what it reflects
                        color.rgb += probe_add * probe_lit;
                        color.rgb = mix(color.rgb, probe_alb * 1.15, probe_e);
                    }
                """;
        int fog = src.indexOf("fragColor = linear_fog(", main);
        return src.substring(0, main) + decl + src.substring(main, fog) + shade.stripTrailing() + "\n    " + src.substring(fog);
    }

    // ------------------------------------------------------------------ binding

    private static final Map<RenderType, AbstractTexture> MASKS = new IdentityHashMap<>();
    private static final AbstractTexture NONE = new AbstractTexture() {
        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager rm) {}
    };
    private static final Map<Integer, int[]> PROGRAMS = new java.util.HashMap<>();
    private static Field stateField, textureField;
    private static Method cutoutTexture;
    private static boolean reflectFailed;

    /** the type's texture (a composite render type's texture shard), or null */
    private static ResourceLocation texture(RenderType t) {
        if (reflectFailed) return null;
        try {
            if (stateField == null) {
                Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                stateField = composite.getDeclaredField("state");
                stateField.setAccessible(true);
                textureField = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState").getDeclaredField("textureState");
                textureField.setAccessible(true);
                cutoutTexture = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard").getDeclaredMethod("cutoutTexture");
                cutoutTexture.setAccessible(true);
            }
            if (!stateField.getDeclaringClass().isInstance(t)) return null;
            Object tex = textureField.get(stateField.get(t));
            Object o = cutoutTexture.invoke(tex);
            return o instanceof Optional<?> opt && opt.orElse(null) instanceof ResourceLocation rl ? rl : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectFailed = true;
            System.out.println("[probe/opt] materials: render type texture not readable (" + e + "), off");
            return null;
        }
    }

    private static AbstractTexture mask(RenderType t) {
        AbstractTexture m = MASKS.get(t);
        if (m != null) return m;
        m = NONE;
        ResourceLocation rl = texture(t);
        if (rl != null && rl.getPath().endsWith(".png")) {
            ResourceLocation s = rl.withPath(rl.getPath().substring(0, rl.getPath().length() - 4) + "_s.png");
            if (Minecraft.getInstance().getResourceManager().getResource(s).isPresent()) m = Minecraft.getInstance().getTextureManager().getTexture(s);
            else missing++;
        }
        MASKS.put(t, m);
        return m;
    }

    /** resource reload: masks are looked up again */
    public static void clear() {
        MASKS.clear();
        MIPPED.clear();
    }

    private static final Map<RenderType, Boolean> MIPPED = new IdentityHashMap<>();
    public static long mipmapped;

    /**
     * A model texture of 128 px and up gets mipmaps (vanilla builds none for entities): minified, a dense texture
     * otherwise breaks into shimmering noise; magnified it stays nearest (crisp texels up close). Its mask too.
     */
    private static void mipmaps(RenderType t, AbstractTexture mask) {
        if (MIPPED.containsKey(t)) return;
        MIPPED.put(t, Boolean.TRUE);
        ResourceLocation rl = texture(t);
        if (rl == null) return;
        int active = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + UNIT);
        for (AbstractTexture tex : new AbstractTexture[] {Minecraft.getInstance().getTextureManager().getTexture(rl), mask}) {
            if (tex == null || tex == NONE) continue;
            RenderSystem.bindTexture(tex.getId());
            int w = org.lwjgl.opengl.GL11C.glGetTexLevelParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH);
            if (w < 128) continue;
            int levels = 31 - Integer.numberOfLeadingZeros(w);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL, levels);
            org.lwjgl.opengl.GL30C.glGenerateMipmap(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
            mipmapped++;
        }
        RenderSystem.activeTexture(active);
    }

    /** PartBatch, before a mode 1 / 3 draw of this type with this shader: true if a mask was bound (end() after the draw) */
    static boolean begin(ShaderInstance shader, RenderType t) {
        if (!ON || t == null) return false;
        AbstractTexture m = mask(t);
        mipmaps(t, m);
        if (m == NONE) return false;
        int[] loc = PROGRAMS.computeIfAbsent(shader.getId(), id -> new int[] {GL20C.glGetUniformLocation(id, "ProbeMaterial"), GL20C.glGetUniformLocation(id, "ProbeMaterialOn")});
        if (loc[0] < 0 || loc[1] < 0) return false;
        int active = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + UNIT);
        RenderSystem.bindTexture(m.getId());
        RenderSystem.activeTexture(active);
        GL20C.glUniform1i(loc[0], UNIT);
        GL20C.glUniform1i(loc[1], 1);
        bound++;
        return true;
    }

    static void end(ShaderInstance shader) {
        int[] loc = PROGRAMS.get(shader.getId());
        if (loc != null && loc[1] >= 0) GL20C.glUniform1i(loc[1], 0);
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "materials: %s, %d draws with a mask, %d types without, %d textures mipmapped (128 px and up)", ON ? "on" : "off", bound, missing, mipmapped);
    }
}
