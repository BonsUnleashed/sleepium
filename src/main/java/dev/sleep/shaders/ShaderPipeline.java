package dev.sleep.shaders;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

/**
 * A loaded shaderpack running the level render: -Dsleep.shaders=<pack> (from shaderpacks/). Vanilla draws, with the
 * pack's gbuffers programs put in place of vanilla's shaders (by vanilla shader and render phase), into the pack's
 * framebuffer; deferred passes run before the translucents, composite + final after the hand, final into the window.
 * The hooks (sleep-shaders.mixins.json) do nothing while no pack is loaded.
 */
public final class ShaderPipeline {
    private static ShaderPipeline current;
    /** a pack is loaded (the hooks check this first) */
    public static volatile boolean active;

    public static ShaderPipeline current() {
        return current;
    }

    final ShaderPackSource src;
    final ProgramSet set;
    final PackProperties props;
    final RenderTargets targets = new RenderTargets();
    final PackUniforms uniforms = new PackUniforms();
    private final Map<String, CompositeProgram> passes = new HashMap<>();
    private final List<CompositeProgram> deferred = new ArrayList<>(), composite = new ArrayList<>(), begin = new ArrayList<>(), prepare = new ArrayList<>();
    private CompositeProgram finalPass;
    private final Map<String, PackShader> shaders = new HashMap<>();
    private final Map<String, Integer> stageTextures = new HashMap<>();   // "<stage>.<sampler>" -> texture id
    private int noiseTex, normalsTex, specularTex, shadowDepthTex, shadowColorTex;
    private static final Map<String, Integer> UNITS = new HashMap<>(RenderTargets.UNITS);
    private int generation;
    public boolean inLevel;
    private boolean deferredDone, handPhase;
    private String phase = "";
    private final List<String> problems = new ArrayList<>();

    public static Map<String, Integer> samplerUnits() {
        return UNITS;
    }

    // ------------------------------------------------------------------ loading

    /** render thread, once: the pack named by -Dsleep.shaders, if any */
    public static void loadConfigured() {
        String name = System.getProperty("sleep.shaders");
        if (name == null || name.isBlank() || current != null) return;
        try {
            current = new ShaderPipeline(Minecraft.getInstance().gameDirectory.toPath(), name);
            active = true;
            dev.sleep.optsupport.LightVolumes.recipesIgnored("a shaderpack's terrain program");
            var opts = Minecraft.getInstance().options;
            if (opts.graphicsMode().get() == net.minecraft.client.GraphicsStatus.FABULOUS) opts.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);
        } catch (Exception e) {
            System.out.println("[sleep/shaders] could not load " + name + ": " + e);
            e.printStackTrace(System.out);
        }
    }

    private ShaderPipeline(Path gameDir, String name) throws Exception {
        long t0 = System.nanoTime();
        src = ShaderPackSource.open(gameDir.resolve("shaderpacks"), name);
        PackOptions options = PackOptions.scan(src);
        Path userFile = gameDir.resolve("shaderpacks").resolve(src.name + ".txt");
        if (Files.exists(userFile)) options.load(Files.readString(userFile));
        List<String> macros = StandardMacros.build(List.of());
        List<String> withOptions = new ArrayList<>(macros);
        withOptions.addAll(options.asMacros());
        props = PackProperties.load(src, withOptions);
        set = ProgramSet.load(src, options, macros, props, "world0");
        uniforms.configure(set.consts);
        uniforms.custom = CustomUniforms.parse(props.customUniforms);
        problems.addAll(uniforms.custom.errors);
        step("textures");
        loadTextures();
        step("targets");
        boolean[] used = new boolean[RenderTargets.COLOR_TEX];
        for (ProgramSet.Program p : set.programs.values()) {
            for (int b : p.drawBuffers) if (b < used.length) used[b] = true;
            for (String s : p.sources) {
                if (s == null) continue;
                for (int i = 0; i < used.length; i++) if (s.contains("colortex" + i) || i < 8 && s.contains(new String[] {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"}[i])) used[i] = true;
            }
        }
        targets.configure(set.consts, used);
        for (ProgramSet.Program p : set.programs.values()) {
            boolean gb = ProgramSet.FALLBACK.containsKey(p.name);
            if (gb) continue;
            step("pass " + p.name);
            CompositeProgram cp = new CompositeProgram(p);
            if (!cp.log.isEmpty()) problems.add(cp.log);
            passes.put(p.name, cp);
        }
        for (String pass : new String[] {"begin", "prepare", "deferred", "composite"}) {
            List<CompositeProgram> list = switch (pass) {
                case "begin" -> begin;
                case "prepare" -> prepare;
                case "deferred" -> deferred;
                default -> composite;
            };
            for (int i = 0; i < 100; i++) {
                CompositeProgram cp = passes.get(i == 0 ? pass : pass + i);
                if (cp != null && (cp.id != 0 || !cp.computes.isEmpty())) list.add(cp);
            }
        }
        finalPass = passes.get("final");
        generation++;
        System.out.printf("[sleep/shaders] loaded %s: %d programs, %d deferred, %d composite, final %s, %d custom uniforms, %.0f ms%s%n", src.name,
                set.programs.size(), deferred.size(), composite.size(), finalPass != null && finalPass.id != 0, uniforms.custom.entries.size(),
                (System.nanoTime() - t0) / 1e6, problems.isEmpty() ? "" : "; problems:\n" + String.join("\n", problems));
    }

    private static final boolean STEPS = Boolean.getBoolean("sleep.shaders.steps");

    private static void step(String s) {
        if (STEPS) {
            System.out.println("[sleep/shaders] step " + s);
            System.out.flush();
        }
    }

    private void loadTextures() {
        int res = (int) Float.parseFloat(set.consts.getOrDefault("noiseTextureResolution", "256"));
        String noisePath = props.get("texture.noise");
        noiseTex = noisePath != null ? packTexture(noisePath) : 0;
        if (noiseTex == 0) {
            java.util.Random r = new java.util.Random(0);
            byte[] px = new byte[res * res * 3];
            r.nextBytes(px);
            noiseTex = rawTexture(res, res, GL11.GL_RGB8, GL11.GL_RGB, px, true);
        }
        step("defaults");
        normalsTex = rawTexture(1, 1, GL11.GL_RGBA8, GL11.GL_RGBA, new byte[] {127, 127, (byte) 255, (byte) 255}, false);
        specularTex = rawTexture(1, 1, GL11.GL_RGBA8, GL11.GL_RGBA, new byte[] {0, 0, 0, 0}, false);
        // no shadow pass yet: a far depth (everything lit) and a white colour
        step("shadow");
        shadowDepthTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowDepthTex);
        GL43.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_DEPTH_COMPONENT32F, 1, 1);
        org.lwjgl.opengl.GL44.glClearTexImage(shadowDepthTex, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, new float[] {1});
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        step("shadow colour");
        shadowColorTex = rawTexture(1, 1, GL11.GL_RGBA8, GL11.GL_RGBA, new byte[] {-1, -1, -1, -1}, false);
        int nextUnit = 38;
        for (var e : props.all.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("customTexture.")) {
                String sampler = k.substring("customTexture.".length());
                int tex = resolveTexture(e.getValue());
                if (tex == 0) continue;
                UNITS.put(sampler, nextUnit++);
                for (String stage : new String[] {"gbuffers", "deferred", "composite", "prepare", "begin"}) stageTextures.put(stage + "." + sampler, tex);
            } else if (k.startsWith("texture.") && !k.equals("texture.noise")) {
                String[] parts = k.split("\\.", 3);
                if (parts.length < 3) continue;
                int tex = resolveTexture(e.getValue());
                if (tex != 0) stageTextures.put(parts[1] + "." + parts[2], tex);
            }
        }
    }

    /** a pack file (png), a resource location (minecraft:textures/...), or the block atlas */
    private int resolveTexture(String value) {
        step("texture " + value);
        String v = value.trim().split("\\s+")[0];
        if (v.contains(":")) {
            ResourceLocation rl = ResourceLocation.parse(v);
            return Minecraft.getInstance().getTextureManager().getTexture(rl).getId();
        }
        return packTexture(v);
    }

    private int packTexture(String path) {
        byte[] bytes = src.bytes(ShaderPackSource.normalize(path));
        if (bytes == null) {
            problems.add("texture not in the pack: " + path);
            return 0;
        }
        try (NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            int w = img.getWidth(), h = img.getHeight();
            byte[] px = new byte[w * h * 4];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    int o = (y * w + x) * 4;
                    px[o] = (byte) abgr;
                    px[o + 1] = (byte) (abgr >> 8);
                    px[o + 2] = (byte) (abgr >> 16);
                    px[o + 3] = (byte) (abgr >> 24);
                }
            String meta = src.text(ShaderPackSource.normalize(path) + ".mcmeta");
            boolean blur = meta != null && meta.replace(" ", "").contains("\"blur\":true"), clamp = meta != null && meta.replace(" ", "").contains("\"clamp\":true");
            return upload(id, w, h, GL11.GL_RGBA8, GL11.GL_RGBA, px, blur, !clamp);
        } catch (Exception e) {
            problems.add("texture " + path + ": " + e);
            return 0;
        }
    }

    private static int rawTexture(int w, int h, int internal, int format, byte[] px, boolean repeat) {
        return upload(GL11.glGenTextures(), w, h, internal, format, px, true, repeat);
    }

    private static int upload(int id, int w, int h, int internal, int format, byte[] px, boolean linear, boolean repeat) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memAlloc(px.length);
        buf.put(px).flip();
        // vanilla's texture uploads leave the unpack state set (row length = an atlas's width): rows would be read past
        // this buffer
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, format, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        org.lwjgl.system.MemoryUtil.memFree(buf);
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST, wrap = repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        return id;
    }

    // ------------------------------------------------------------------ the frame

    /** LevelRenderer.renderLevel, after vanilla's clear */
    public void beginLevel(Camera camera, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick) {
        var main = Minecraft.getInstance().getMainRenderTarget();
        targets.resize(main.width, main.height);
        uniforms.beginFrame(camera, frustumMatrix, projectionMatrix, partialTick, main.width, main.height);
        float[] fog = RenderSystem.getShaderFogColor();
        targets.clearAll(fog);
        inLevel = true;
        deferredDone = false;
        handPhase = false;
        phase = "";
        for (CompositeProgram cp : begin) runPass(cp, "begin", false);
        for (CompositeProgram cp : prepare) runPass(cp, "prepare", false);
        bindTextures("gbuffers");
        bindGbuffers(new int[] {0});
    }

    /** a profiler section of renderLevel starts */
    public void phase(String name) {
        if (!inLevel) return;
        phase = name;
        uniforms.renderStage = switch (name) {
            case "sky" -> 1;
            case "terrain" -> 8;
            case "entities" -> 11;
            case "blockentities" -> 12;
            case "destroyProgress" -> 13;
            case "outline" -> 14;
            case "translucent" -> 17;
            case "string" -> 18;
            case "particles", "solid_particles" -> 19;
            case "clouds" -> 20;
            case "weather" -> 21;
            default -> uniforms.renderStage;
        };
        if (name.equals("translucent") && !deferredDone) {
            deferredDone = true;
            targets.copyDepth(targets.depthTex1);
            for (CompositeProgram cp : deferred) runPass(cp, "deferred", false);
            bindTextures("gbuffers");
            bindGbuffers(new int[] {0});
        }
    }

    /** GameRenderer, before the hand: depthtex2, the hand phase */
    public void beforeHand() {
        if (!inLevel) return;
        if (!deferredDone) phase("translucent");
        targets.copyDepth(targets.depthTex2);
        handPhase = true;
        phase = "hand";
        uniforms.renderStage = 16;
        bindGbuffers(new int[] {0});
    }

    /** GameRenderer.renderLevel's end: composite, final into the window */
    public void endLevel() {
        if (!inLevel) return;
        if (!deferredDone) phase("translucent");
        inLevel = false;
        for (CompositeProgram cp : composite) runPass(cp, "composite", false);
        if (finalPass != null && finalPass.id != 0) runPass(finalPass, "composite", true);
        else blitToMain();
        restoreVanillaState();
    }

    /** the gbuffers framebuffer for these draw buffers (vanilla's main-target binds land here during the level) */
    public void bindGbuffers(int[] drawBuffers) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targets.framebuffer(drawBuffers, false, true));
        GL11.glViewport(0, 0, targets.width, targets.height);
    }

    private void runPass(CompositeProgram cp, String stage, boolean isFinal) {
        for (int i = 0; i < RenderTargets.COLOR_TEX; i++) if (cp.wantsMipmap[i]) targets.generateMipmap(i);
        bindTextures(stage);
        int[] db = cp.program.drawBuffers;
        if (isFinal) {
            var main = Minecraft.getInstance().getMainRenderTarget();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, main.width, main.height);
        } else {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targets.framebuffer(db, true, false));
            GL11.glViewport(0, 0, targets.width, targets.height);
        }
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColorMask(true, true, true, true);
        if (cp.id != 0) {
            GL20.glUseProgram(cp.id);
            uniforms.uploadFrame(cp.binding);
            uniforms.uploadDraw(cp.binding, IDENTITY, IDENTITY);
            CompositeProgram.drawQuad();
        }
        if (!isFinal && cp.id != 0) {
            for (int b : db) {
                String f = props.get("flip." + cp.program.name + ".colortex" + b);
                if (f == null || f.equals("true")) targets.flip(b);
            }
        }
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    /** no final program: colortex0 to the window as it is */
    private void blitToMain() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        int read = targets.framebuffer(new int[] {0}, false, false);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, targets.width, targets.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    /** the pack textures on their fixed units (main of each colortex; a stage's custom textures in place of samplers) */
    void bindTextures(String stage) {
        int active = com.mojang.blaze3d.platform.GlStateManager._getActiveTexture();
        for (int i = 0; i < RenderTargets.COLOR_TEX; i++) {
            Integer custom = stageTextures.get(stage + ".colortex" + i);
            if (custom == null && i >= 4 && i < 8) custom = stageTextures.get(stage + ".gaux" + (i - 3));
            int tex = custom != null ? custom : targets.used[i] ? targets.main(i) : 0;
            bind(12 + i, tex);
        }
        bind(28, targets.depthTex0);
        bind(29, targets.depthTex1);
        bind(30, targets.depthTex2);
        bind(31, shadowDepthTex);
        bind(32, shadowDepthTex);
        bind(33, shadowColorTex);
        bind(34, shadowColorTex);
        bind(35, noiseTex);
        bind(36, normalsTex);
        bind(37, specularTex);
        for (var e : UNITS.entrySet()) {
            if (e.getValue() < 38) continue;
            Integer t = stageTextures.get(stage + "." + e.getKey());
            if (t != null) bind(e.getValue(), t);
        }
        GL13.glActiveTexture(active);
    }

    private static void bind(int unit, int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    /** after our raw GL: vanilla's caches (program in use, immediate buffer) see the truth again */
    private void restoreVanillaState() {
        GL20.glUseProgram(0);
        ShaderPipelineHooks.resetProgramCache();
        com.mojang.blaze3d.vertex.BufferUploader.reset();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    // ------------------------------------------------------------------ programs in place of vanilla's

    /** the pack's program for a vanilla shader in the current phase, or vanilla's own */
    public ShaderInstance shaderFor(ShaderInstance vanilla) {
        if (!inLevel || vanilla == null || vanilla instanceof PackShader) return vanilla;
        String program = programFor(vanilla.getName());
        if (program == null) return vanilla;
        ProgramSet.Program p = set.resolve(program);
        if (p == null || p.sources[0] == null || p.sources[2] == null) return vanilla;
        String key = vanilla.getName() + "|" + p.name;
        PackShader s = shaders.get(key);
        if (s == null && !shaders.containsKey(key)) {
            try {
                s = PackShader.create("sleep_pack" + generation + "_" + p.name + "_" + Integer.toHexString(vanilla.getName().hashCode()), p, vanilla.getVertexFormat(),
                        alphaTest(vanilla.getName(), program));
            } catch (Exception e) {
                System.out.println("[sleep/shaders] " + p.name + " for " + vanilla.getName() + ": " + e);
                s = null;
            }
            shaders.put(key, s);
        }
        return s != null ? s : vanilla;
    }

    /**
     * The alpha test of a legacy program for a vanilla shader: alphaTest.<program> from the pack, else Iris 1.8.14's
     * default for that kind of geometry (ShaderKey: solid / translucent terrain, lines, beams, sky off; cutout, entities,
     * particles, text, weather, clouds 0.1; glint, eyes, plain textured / coloured 1e-4).
     */
    String alphaTest(String vanilla, String program) {
        String p = props.get("alphaTest." + program);
        if (p != null) return p.equalsIgnoreCase("off") ? null : p;
        switch (vanilla) {
            case "rendertype_solid", "rendertype_translucent", "rendertype_tripwire", "rendertype_lightning", "rendertype_leash", "rendertype_beacon_beam",
                    "rendertype_lines", "position", "rendertype_entity_solid", "rendertype_end_portal", "rendertype_end_gateway":
                return null;
            case "rendertype_eyes", "rendertype_glint", "rendertype_glint_translucent", "rendertype_entity_glint", "rendertype_entity_glint_direct",
                    "rendertype_armor_glint", "rendertype_armor_entity_glint", "position_color", "position_tex":
                return "GREATER 0.0001";
            default:
        }
        if (phase.equals("sky")) return null;
        return "GREATER 0.1";
    }

    String programFor(String v) {
        switch (phase) {
            case "sky":
                return v.equals("position_tex") || v.equals("position_tex_color") ? "gbuffers_skytextured" : "gbuffers_skybasic";
            case "clouds":
                return "gbuffers_clouds";
            case "weather":
                return v.equals("rendertype_world_border") ? "gbuffers_textured" : v.startsWith("particle") ? "gbuffers_weather" : null;
            default:
        }
        boolean hand = handPhase, be = phase.equals("blockentities");
        switch (v) {
            case "rendertype_solid", "rendertype_cutout_mipped", "rendertype_cutout":
                return hand ? "gbuffers_hand" : be ? "gbuffers_block" : "gbuffers_terrain";
            case "rendertype_translucent", "rendertype_tripwire", "rendertype_translucent_moving_block":
                return hand ? "gbuffers_hand_water" : be ? "gbuffers_block_translucent" : "gbuffers_water";
            case "particle":
                return phase.equals("particles") ? "gbuffers_particles_translucent" : "gbuffers_particles";
            case "rendertype_lines":
                return "gbuffers_line";
            case "rendertype_crumbling":
                return "gbuffers_damagedblock";
            case "rendertype_beacon_beam":
                return "gbuffers_beaconbeam";
            case "rendertype_lightning":
                return "gbuffers_lightning";
            case "rendertype_eyes":
                return "gbuffers_spidereyes";
            case "rendertype_armor_glint", "rendertype_armor_entity_glint", "rendertype_glint", "rendertype_glint_translucent", "rendertype_entity_glint",
                    "rendertype_entity_glint_direct":
                return "gbuffers_armor_glint";
            case "rendertype_leash", "position_color", "rendertype_debug_line_strip", "rendertype_debug_quads":
                return "gbuffers_basic";
            case "rendertype_world_border":
                return "gbuffers_textured";
            case "rendertype_end_portal", "rendertype_end_gateway":
                return null;
            default:
        }
        if (v.startsWith("rendertype_entity") || v.startsWith("rendertype_item_entity") || v.startsWith("rendertype_armor") || v.startsWith("rendertype_text")
                || v.startsWith("rendertype_energy_swirl") || v.startsWith("rendertype_breeze_wind")) {
            boolean translucent = v.contains("translucent");
            if (hand) return translucent ? "gbuffers_hand_water" : "gbuffers_hand";
            if (be) return translucent ? "gbuffers_block_translucent" : "gbuffers_block";
            return translucent ? "gbuffers_entities_translucent" : "gbuffers_entities";
        }
        return null;
    }

    // ------------------------------------------------------------------ blend overrides (blend.<program>[.<buffer>])

    private final int[] savedBlend = new int[4];
    private boolean savedBlendOn, perBufferApplied;

    void applyBlend(String program) {
        String all = props.get("blend." + program);
        if (all != null) {
            if (all.equalsIgnoreCase("off")) RenderSystem.disableBlend();
            else {
                int[] f = factors(all);
                if (f != null) {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(f[0], f[1], f[2], f[3]);
                }
            }
        }
        perBufferApplied = false;
        ProgramSet.Program p = set.programs.get(program);
        if (p == null) return;
        for (int i = 0; i < p.drawBuffers.length; i++) {
            String b = props.get("blend." + program + ".colortex" + p.drawBuffers[i]);
            if (b == null) continue;
            if (!perBufferApplied) {
                perBufferApplied = true;
                savedBlendOn = GL11.glIsEnabled(GL11.GL_BLEND);
                savedBlend[0] = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
                savedBlend[1] = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
                savedBlend[2] = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
                savedBlend[3] = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            }
            if (b.equalsIgnoreCase("off")) GL30.glDisablei(GL11.GL_BLEND, i);
            else {
                int[] f = factors(b);
                if (f != null) {
                    GL30.glEnablei(GL11.GL_BLEND, i);
                    GL40.glBlendFuncSeparatei(i, f[0], f[1], f[2], f[3]);
                }
            }
        }
    }

    void clearBlend(String program) {
        if (!perBufferApplied) return;
        perBufferApplied = false;
        if (savedBlendOn) GL11.glEnable(GL11.GL_BLEND);
        else GL11.glDisable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(savedBlend[0], savedBlend[1], savedBlend[2], savedBlend[3]);
    }

    private static int[] factors(String s) {
        String[] p = s.trim().split("\\s+");
        if (p.length != 4) return null;
        int[] f = new int[4];
        for (int i = 0; i < 4; i++) {
            f[i] = switch (p[i]) {
                case "ZERO" -> GL11.GL_ZERO;
                case "ONE" -> GL11.GL_ONE;
                case "SRC_COLOR" -> GL11.GL_SRC_COLOR;
                case "ONE_MINUS_SRC_COLOR" -> GL11.GL_ONE_MINUS_SRC_COLOR;
                case "DST_COLOR" -> GL11.GL_DST_COLOR;
                case "ONE_MINUS_DST_COLOR" -> GL11.GL_ONE_MINUS_DST_COLOR;
                case "SRC_ALPHA" -> GL11.GL_SRC_ALPHA;
                case "ONE_MINUS_SRC_ALPHA" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
                case "DST_ALPHA" -> GL11.GL_DST_ALPHA;
                case "ONE_MINUS_DST_ALPHA" -> GL11.GL_ONE_MINUS_DST_ALPHA;
                case "SRC_ALPHA_SATURATE" -> GL11.GL_SRC_ALPHA_SATURATE;
                default -> -1;
            };
            if (f[i] < 0) return null;
        }
        return f;
    }
}
