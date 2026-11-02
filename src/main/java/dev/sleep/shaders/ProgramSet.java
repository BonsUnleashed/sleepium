package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The programs of one dimension of a pack: found in its world folder (world0 / world-1 / world1 when the pack has
 * them, else shaders/), preprocessed with the options and standard macros, translated to core GLSL, their directives
 * read (DRAWBUFFERS / RENDERTARGETS: the last one in the preprocessed fragment source; const directives: formats,
 * clears, mipmaps, shadow settings - pack-wide, from every program). A gbuffers / shadow program the pack lacks (or
 * disables with program.X.enabled) is served by its fallback chain (Iris 1.8.14 ProgramId).
 */
public final class ProgramSet {
    /** gbuffers / shadow program -> its fallback */
    public static final Map<String, String> FALLBACK = new LinkedHashMap<>();

    static {
        String[][] f = {{"gbuffers_basic", null}, {"gbuffers_line", "gbuffers_basic"}, {"gbuffers_textured", "gbuffers_basic"},
                {"gbuffers_textured_lit", "gbuffers_textured"}, {"gbuffers_skybasic", "gbuffers_basic"}, {"gbuffers_skytextured", "gbuffers_textured"},
                {"gbuffers_clouds", "gbuffers_textured"}, {"gbuffers_terrain", "gbuffers_textured_lit"}, {"gbuffers_terrain_solid", "gbuffers_terrain"},
                {"gbuffers_terrain_cutout", "gbuffers_terrain"}, {"gbuffers_damagedblock", "gbuffers_terrain"}, {"gbuffers_block", "gbuffers_terrain"},
                {"gbuffers_block_translucent", "gbuffers_block"}, {"gbuffers_beaconbeam", "gbuffers_textured"}, {"gbuffers_item", "gbuffers_textured_lit"},
                {"gbuffers_entities", "gbuffers_textured_lit"}, {"gbuffers_entities_translucent", "gbuffers_entities"},
                {"gbuffers_entities_glowing", "gbuffers_entities"}, {"gbuffers_lightning", "gbuffers_entities"}, {"gbuffers_particles", "gbuffers_textured_lit"},
                {"gbuffers_particles_translucent", "gbuffers_particles"}, {"gbuffers_armor_glint", "gbuffers_textured"},
                {"gbuffers_spidereyes", "gbuffers_textured"}, {"gbuffers_hand", "gbuffers_textured_lit"}, {"gbuffers_weather", "gbuffers_textured_lit"},
                {"gbuffers_water", "gbuffers_terrain"}, {"gbuffers_hand_water", "gbuffers_hand"}, {"shadow", null}, {"shadow_solid", "shadow"},
                {"shadow_cutout", "shadow"}, {"shadow_water", "shadow"}, {"shadow_entities", "shadow"}, {"shadow_block", "shadow"},
                {"shadow_lightning", "shadow_entities"}};
        for (String[] e : f) FALLBACK.put(e[0], e[1]);
    }

    /** composite-style passes in frame order (each: name, name1..name99) */
    public static final String[] PASSES = {"begin", "shadowcomp", "prepare", "deferred", "composite"};

    public static final class Program {
        public final String name;
        /** translated sources: vertex, geometry, fragment, compute (null = absent) */
        public final String[] sources = new String[4];
        /** compute shaders attached to a composite-style program (P_a .. P_z), translated */
        public final List<String> computes = new ArrayList<>();
        /** the colour targets written, in output order (DRAWBUFFERS / RENDERTARGETS; default 0..7) */
        public int[] drawBuffers;
        public boolean explicitDrawBuffers;
        /** const directives of this program's sources (name -> value text) */
        public final Map<String, String> consts = new LinkedHashMap<>();
        public final List<String> errors = new ArrayList<>();

        Program(String name) {
            this.name = name;
        }

        public boolean computeOnly() {
            return sources[0] == null && sources[2] == null && sources[3] != null;
        }
    }

    public final String folder;
    public final Map<String, Program> programs = new LinkedHashMap<>();
    /** consts of every program (later programs do not override earlier ones: first wins, as most packs set them once) */
    public final Map<String, String> consts = new LinkedHashMap<>();

    private ProgramSet(String folder) {
        this.folder = folder;
    }

    /** dim: "world0", "world-1", "world1" (the folder used when the pack has it) */
    public static ProgramSet load(ShaderPackSource src, PackOptions options, List<String> macros, PackProperties props, String dim) {
        String folder = src.list(dim).isEmpty() ? "" : dim + "/";
        ProgramSet set = new ProgramSet(folder);
        List<String> optionMacros = new ArrayList<>(macros);
        optionMacros.addAll(options.asMacros());
        List<String> names = new ArrayList<>(FALLBACK.keySet());
        for (String p : PASSES) {
            names.add(p);
            for (int i = 1; i < 100; i++) names.add(p + i);
        }
        names.add("final");
        for (String name : names) {
            if (!props.programEnabled(folder, name, optionMacros)) continue;
            Program prog = set.loadProgram(src, options, macros, name);
            if (prog != null) set.programs.put(name, prog);
        }
        for (Program p : set.programs.values()) for (var e : p.consts.entrySet()) set.consts.putIfAbsent(e.getKey(), e.getValue());
        return set;
    }

    private static final String[] EXT = {"vsh", "gsh", "fsh", "csh"};

    private Program loadProgram(ShaderPackSource src, PackOptions options, List<String> macros, String name) {
        String base = folder + name;
        boolean any = false;
        for (String e : EXT) any |= src.exists(base + "." + e);
        if (!any) return null;
        Program p = new Program(name);
        boolean gbuffers = name.startsWith("gbuffers_") || name.startsWith("shadow") && !name.startsWith("shadowcomp");
        String fragPre = null;
        for (int k = 0; k < 4; k++) {
            String file = base + "." + EXT[k];
            if (!src.exists(file)) continue;
            GlslPreprocessor pp = new GlslPreprocessor(src::text, macros, options::rewrite);
            String pre = pp.process(file);
            for (String err : pp.errors()) p.errors.add(EXT[k] + ": " + err);
            readConsts(pre, p.consts);
            if (k == 2) fragPre = pre;
            GlslTranslator.Result r = GlslTranslator.translate(pre, GlslTranslator.Stage.values()[k], gbuffers);
            for (String n : r.notes()) p.errors.add(EXT[k] + ": " + n);
            p.sources[k] = r.text();
        }
        if (!gbuffers) {   // compute attachments P_a .. P_z
            for (char c = 'a'; c <= 'z'; c++) {
                String file = base + "_" + c + ".csh";
                if (!src.exists(file)) continue;
                GlslPreprocessor pp = new GlslPreprocessor(src::text, macros, options::rewrite);
                String pre = pp.process(file);
                readConsts(pre, p.consts);
                p.computes.add(GlslTranslator.translate(pre, GlslTranslator.Stage.COMPUTE).text());
            }
        }
        int[] db = fragPre == null ? null : drawBuffers(fragPre);
        p.explicitDrawBuffers = db != null;
        p.drawBuffers = db != null ? db : new int[] {0, 1, 2, 3, 4, 5, 6, 7};
        return p;
    }

    /** the served program for name: itself, or the first of its fallback chain the pack has (null: none) */
    public Program resolve(String name) {
        for (String n = name; n != null; n = FALLBACK.get(n)) {
            Program p = programs.get(n);
            if (p != null) return p;
        }
        return null;
    }

    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern RENDERTARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+?)\\s*\\*/");

    /** the last DRAWBUFFERS / RENDERTARGETS directive of the preprocessed source, or null */
    static int[] drawBuffers(String pre) {
        int at = -1;
        int[] found = null;
        Matcher m = DRAWBUFFERS.matcher(pre);
        while (m.find()) {
            if (m.start() < at) continue;
            at = m.start();
            String d = m.group(1);
            found = new int[d.length()];
            for (int i = 0; i < d.length(); i++) found[i] = d.charAt(i) - '0';
        }
        m = RENDERTARGETS.matcher(pre);
        while (m.find()) {
            if (m.start() < at) continue;
            at = m.start();
            String[] parts = m.group(1).split(",");
            found = new int[parts.length];
            for (int i = 0; i < parts.length; i++) found[i] = Integer.parseInt(parts[i].trim());
        }
        return found;
    }

    private static final Pattern CONST = Pattern.compile("\\bconst\\s+(int|float|bool|vec2|vec3|vec4|ivec3)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;]+);");

    /** const directives, line by line, comment state ignored (formats live in comments) */
    static void readConsts(String pre, Map<String, String> out) {
        Matcher m = CONST.matcher(pre);
        while (m.find()) if (DIRECTIVE_CONSTS.matcher(m.group(2)).matches()) out.putIfAbsent(m.group(2), m.group(3).trim());
    }

    /** the const names that are directives to the engine (not ordinary GLSL constants) */
    private static final Pattern DIRECTIVE_CONSTS = Pattern.compile("(colortex\\d+|gcolor|gdepth|gnormal|composite|gaux\\d|shadowcolor\\d?)(Format|Clear|ClearColor|MipmapEnabled)"
            + "|shadowMapResolution|shadowDistance|shadowDistanceRenderMul|entityShadowDistanceMul|shadowIntervalSize|shadowMapFov|shadowNearPlane|shadowFarPlane"
            + "|voxelDistance|sunPathRotation|shadowHardwareFiltering\\d?|generateShadowMipmap|generateShadowColorMipmap|shadow(tex|color|Color)?\\d?(Mipmap|Nearest|MinMagNearest)"
            + "|shadowtexMipmap|shadowtexNearest|ambientOcclusionLevel|wetnessHalflife|drynessHalflife|eyeBrightnessHalflife|centerDepthHalflife"
            + "|noiseTextureResolution|superSamplingLevel|workGroups|workGroupsRender");
}
