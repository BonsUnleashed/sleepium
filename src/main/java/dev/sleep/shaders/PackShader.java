package dev.sleep.shaders;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * A pack's gbuffers / shadow program as a vanilla ShaderInstance, so every vanilla draw path (RenderType -> shader ->
 * setDefaultUniforms -> apply) runs it: built from in-memory resources (a JSON naming the samplers and vanilla's per-draw
 * uniforms, the translated sources); its va* attributes bound to the vertex format's element order; after vanilla's
 * apply it binds the pack framebuffer for its draw buffers and uploads the pack uniforms (frame values once per frame,
 * ids / stage / derived matrices per draw).
 */
public final class PackShader extends ShaderInstance {
    final ProgramSet.Program program;
    final PackUniforms.Binding binding;
    private final Matrix4f mv = new Matrix4f(), proj = new Matrix4f();
    private boolean haveMatrices;

    private PackShader(ResourceProvider rp, String name, VertexFormat format, ProgramSet.Program program) throws IOException {
        super(rp, name, format);
        this.program = program;
        int prev = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(getId());
        this.binding = PackUniforms.bind(getId(), ShaderPipeline.samplerUnits());
        GL20.glUseProgram(prev);
    }

    /** vanilla's per-draw uniforms, declared only when the program uses them */
    private static final String[][] VANILLA_UNIFORMS = {{"ModelViewMat", "matrix4x4", "16"}, {"ProjMat", "matrix4x4", "16"}, {"TextureMat", "matrix4x4", "16"},
            {"ChunkOffset", "float", "3"}, {"ColorModulator", "float", "4"}, {"FogStart", "float", "1"}, {"FogEnd", "float", "1"}, {"FogColor", "float", "4"},
            {"FogShape", "int", "1"}, {"GameTime", "float", "1"}, {"ScreenSize", "float", "2"}, {"LineWidth", "float", "1"}, {"GlintAlpha", "float", "1"},
            {"Light0_Direction", "float", "3"}, {"Light1_Direction", "float", "3"}};

    /**
     * alphaTest: "GREATER 0.1" style (func ref) or null - Iris's alpha test for a legacy program writing gl_FragData:
     * its main is wrapped and the fragment discarded when output 0's alpha fails the test.
     */
    static PackShader create(String uniqueName, ProgramSet.Program p, VertexFormat format, String alphaTest, String requested) throws IOException {
        String[] pro = ShaderPrologue.apply(p.sources[0], p.sources[2], format, requested.startsWith("gbuffers_block"));
        String vsh = pro[0], fsh = withAlphaTest(pro[1], alphaTest);
        if (Boolean.getBoolean("sleep.shaders.dump")) {   // the sources as compiled, per instance
            java.nio.file.Path d = Minecraft.getInstance().gameDirectory.toPath().resolve("sleep/shaders/instances");
            java.nio.file.Files.createDirectories(d);
            java.nio.file.Files.writeString(d.resolve(uniqueName + ".vsh"), vsh);
            java.nio.file.Files.writeString(d.resolve(uniqueName + ".fsh"), fsh);
        }
        StringBuilder json = new StringBuilder("{\"vertex\":\"").append(uniqueName).append("\",\"fragment\":\"").append(uniqueName).append("\",\"samplers\":[");
        boolean first = true;
        for (int i = 0; i < 12; i++) {
            String s = "Sampler" + i;
            if (!word(vsh, s) && !word(fsh, s)) continue;
            json.append(first ? "" : ",").append("{\"name\":\"").append(s).append("\"}");
            first = false;
        }
        json.append("],\"uniforms\":[");
        first = true;
        for (String[] u : VANILLA_UNIFORMS) {
            if (!word(vsh, u[0]) && !word(fsh, u[0])) continue;
            int n = Integer.parseInt(u[2]);
            StringBuilder vals = new StringBuilder();
            for (int i = 0; i < n; i++) vals.append(i == 0 ? "" : ",").append(n == 16 && i % 5 == 0 ? "1.0" : "0.0");
            json.append(first ? "" : ",").append("{\"name\":\"").append(u[0]).append("\",\"type\":\"").append(u[1]).append("\",\"count\":").append(n)
                    .append(",\"values\":[").append(vals).append("]}");
            first = false;
        }
        json.append("]}");
        var vanillaPack = Minecraft.getInstance().getVanillaPackResources();
        String js = json.toString();
        ResourceProvider rp = loc -> {
            String path = loc.getPath();
            String text = path.endsWith(".json") ? js : path.endsWith(".vsh") ? vsh : path.endsWith(".fsh") ? fsh : null;
            if (text == null) return Optional.empty();
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return Optional.of(new Resource(vanillaPack, () -> new ByteArrayInputStream(bytes)));
        };
        return new PackShader(rp, uniqueName, format, p);
    }

    private static final java.util.regex.Pattern MAIN = java.util.regex.Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");

    static String withAlphaTest(String fsh, String test) {
        if (test == null || !word(fsh, "sl_FragData0")) return fsh;
        String[] t = test.trim().split("\\s+");
        if (t.length != 2) return fsh;
        String op = switch (t[0]) {
            case "GREATER" -> ">";
            case "GEQUAL" -> ">=";
            case "LESS" -> "<";
            case "LEQUAL" -> "<=";
            case "EQUAL" -> "==";
            case "NOTEQUAL" -> "!=";
            case "ALWAYS", "GL_ALWAYS" -> null;
            default -> "never";
        };
        if (op == null) return fsh;
        java.util.regex.Matcher m = MAIN.matcher(fsh);
        if (!m.find()) return fsh;
        String cond = op.equals("never") ? "false" : "sl_FragData0.a " + op + " " + t[1];
        return fsh.substring(0, m.start()) + "void sl_main()" + fsh.substring(m.end())
                + "\nvoid main() {\n    sl_main();\n    if (!(" + cond + ")) discard;\n}\n";
    }

    /** name as a whole identifier in the source */
    private static boolean word(String src, String name) {
        return java.util.regex.Pattern.compile("\\b" + name + "\\b").matcher(src).find();
    }

    /** before the link (called from vanilla's constructor): the va* attributes at the format's element indices */
    @Override
    public void attachToProgram() {
        super.attachToProgram();
        int k = 0;
        for (String n : getVertexFormat().getElementAttributeNames()) {
            GL20.glBindAttribLocation(getId(), k, "va" + n);
            GL20.glBindAttribLocation(getId(), k, "sl_raw" + n);   // the prologue's raw UV1 / UV2 (ids in the high bytes)
            k++;
        }
    }

    @Override
    public void setDefaultUniforms(VertexFormat.Mode mode, Matrix4f frustumMatrix, Matrix4f projectionMatrix, Window window) {
        super.setDefaultUniforms(mode, frustumMatrix, projectionMatrix, window);
        mv.set(frustumMatrix);
        proj.set(projectionMatrix);
        haveMatrices = true;
    }

    @Override
    public void apply() {
        super.apply();
        ShaderPipeline pl = ShaderPipeline.current();
        if (pl == null) return;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pl.targets.framebuffer(program.drawBuffers, false, true));
        pl.uniforms.uploadFrame(binding);
        if (!haveMatrices) {
            mv.set(RenderSystem.getModelViewMatrix());
            proj.set(RenderSystem.getProjectionMatrix());
        }
        pl.uniforms.uploadDraw(binding, mv, proj);
        haveMatrices = false;
        pl.applyBlend(program.name);
    }

    @Override
    public void clear() {
        ShaderPipeline pl = ShaderPipeline.current();
        if (pl != null) pl.clearBlend(program.name);
        super.clear();
    }
}
