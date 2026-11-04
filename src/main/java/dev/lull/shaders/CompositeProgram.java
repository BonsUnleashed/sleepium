package dev.lull.shaders;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

/**
 * A composite-style pass (begin / prepare / deferred / composite / final): its own GL program drawing one full-screen
 * quad (vaPosition = vaUV0 in [0,1]^2, ProjMat maps it to clip space, ModelViewMat identity, as Iris gives them), plus
 * its compute attachments dispatched first. Reads every buffer's main texture, writes its draw buffers' alt textures,
 * which then flip; final writes the window's framebuffer.
 */
public final class CompositeProgram {
    final ProgramSet.Program program;
    final int id;
    final PackUniforms.Binding binding;
    final List<int[]> computes = new ArrayList<>();   // {program, groupsX, groupsY, groupsZ}
    final boolean[] wantsMipmap = new boolean[RenderTargets.COLOR_TEX];
    public final String log;

    private static int vao, vbo;

    CompositeProgram(ProgramSet.Program p) {
        this.program = p;
        StringBuilder log = new StringBuilder();
        int prog = 0;
        if (p.sources[0] != null && p.sources[2] != null) {
            prog = link(log, p.name, new int[] {GL20.GL_VERTEX_SHADER, GL20.GL_FRAGMENT_SHADER}, new String[] {p.sources[0], p.sources[2]}, true);
        }
        this.id = prog;
        for (String c : p.computes) {
            int cp = link(log, p.name + "_compute", new int[] {GL43.GL_COMPUTE_SHADER}, new String[] {c}, false);
            if (cp != 0) computes.add(new int[] {cp, 1, 1, 1});
        }
        if (p.sources[3] != null) {
            int cp = link(log, p.name + ".csh", new int[] {GL43.GL_COMPUTE_SHADER}, new String[] {p.sources[3]}, false);
            if (cp != 0) computes.add(0, new int[] {cp, 1, 1, 1});
        }
        this.log = log.toString();
        if (id != 0) {
            int prev = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(id);
            binding = PackUniforms.bind(id, ShaderPipeline.samplerUnits());
            int pl = GL20.glGetUniformLocation(id, "ProjMat"), ml = GL20.glGetUniformLocation(id, "ModelViewMat"), tl = GL20.glGetUniformLocation(id, "TextureMat");
            if (pl >= 0) GL20.glUniformMatrix4fv(pl, false, new float[] {2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, -1, -1, 0, 1});
            float[] ident = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            if (ml >= 0) GL20.glUniformMatrix4fv(ml, false, ident);
            if (tl >= 0) GL20.glUniformMatrix4fv(tl, false, ident);
            GL20.glUseProgram(prev);
        } else binding = null;
        for (var e : p.consts.entrySet()) {
            String k = e.getKey();
            if (k.endsWith("MipmapEnabled") && e.getValue().equals("true")) {
                int i = RenderTargets.colorIndex(k.substring(0, k.length() - "MipmapEnabled".length()));
                if (i >= 0) wantsMipmap[i] = true;
            }
        }
    }

    private static int link(StringBuilder log, String name, int[] types, String[] sources, boolean quad) {
        int prog = GL20.glCreateProgram();
        // a linked binary of the same sources on this driver: no compile (ProgramBinaryCache)
        String key = ProgramBinaryCache.on() ? ProgramBinaryCache.key(String.join("\u0000", sources), quad ? "quad" : "") : null;
        if (key != null && ProgramBinaryCache.has(key)) {
            if (quad) for (int a = 0; a < GlslTranslator.ATTRIBUTES.length; a++) GL20.glBindAttribLocation(prog, a, GlslTranslator.ATTRIBUTES[a][0]);
            if (ProgramBinaryCache.load(prog, key)) return prog;
        }
        List<Integer> shaders = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < types.length; i++) {
            int sh = GL20.glCreateShader(types[i]);
            GL20.glShaderSource(sh, sources[i]);
            GL20.glCompileShader(sh);
            if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                ok = false;
                log.append(name).append(" compile: ").append(GL20.glGetShaderInfoLog(sh, 8192)).append('\n');
            }
            GL20.glAttachShader(prog, sh);
            shaders.add(sh);
        }
        if (quad) for (int a = 0; a < GlslTranslator.ATTRIBUTES.length; a++) GL20.glBindAttribLocation(prog, a, GlslTranslator.ATTRIBUTES[a][0]);
        if (ok) {
            if (key != null) ProgramBinaryCache.retrievable(prog);
            GL20.glLinkProgram(prog);
            if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
                ok = false;
                log.append(name).append(" link: ").append(GL20.glGetProgramInfoLog(prog, 8192)).append('\n');
            } else if (key != null) ProgramBinaryCache.store(prog, key);
        }
        for (int sh : shaders) GL20.glDeleteShader(sh);
        if (!ok) {
            GL20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    /** the full-screen quad: two triangles, positions = UVs in [0,1] (attribute 0 = vaPosition, 2 = vaUV0) */
    static void drawQuad() {
        if (vao == 0) {
            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 12, 0);
            GL30.glBindVertexArray(0);
        }
        GL30.glBindVertexArray(vao);
        GL20.glVertexAttrib4f(1, 1, 1, 1, 1);   // vaColor
        GL20.glVertexAttrib3f(5, 0, 0, 1);   // vaNormal
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
    }

    void delete() {
        if (id != 0) GL20.glDeleteProgram(id);
        for (int[] c : computes) GL20.glDeleteProgram(c[0]);
    }
}
