package dev.sleep.shaders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

/**
 * -Dsleep.shaders.compileTest=<pack>: on the first frame, the pack's overworld program set is loaded (default option
 * values + shaderpacks/<pack>.txt), every program compiled + linked; per program OK or the driver's log, and the table
 * of served programs (fallbacks), draw buffers and pack-wide const directives. Sources as compiled and the logs go to
 * run/sleep/shaders/<pack>/. -Dsleep.shaders.compileTest.quit=true quits.
 */
public final class ShaderPackCompileTest {
    private static boolean done;

    private ShaderPackCompileTest() {}

    public static void maybeRun() {
        if (done) return;
        done = true;
        String pack = System.getProperty("sleep.shaders.compileTest");
        if (pack == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            run(mc.gameDirectory.toPath(), pack);
        } catch (Exception e) {
            System.out.println("[sleep/shaders] compile test failed: " + e);
            e.printStackTrace(System.out);
        }
        if (Boolean.getBoolean("sleep.shaders.compileTest.quit")) mc.stop();
    }

    static void run(Path gameDir, String packName) throws Exception {
        long t0 = System.nanoTime();
        ShaderPackSource src = ShaderPackSource.open(gameDir.resolve("shaderpacks"), packName);
        PackOptions options = PackOptions.scan(src);
        Path userFile = gameDir.resolve("shaderpacks").resolve(src.name + ".txt");
        if (Files.exists(userFile)) options.load(Files.readString(userFile));
        List<String> macros = StandardMacros.build(List.of());
        List<String> withOptions = new ArrayList<>(macros);
        withOptions.addAll(options.asMacros());
        PackProperties props = PackProperties.load(src, withOptions);
        ProgramSet set = ProgramSet.load(src, options, macros, props, "world0");
        long tLoad = System.nanoTime();
        Path out = gameDir.resolve("sleep/shaders/" + src.name);
        Files.createDirectories(out);
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (ProgramSet.Program p : set.programs.values()) {
            StringBuilder log = new StringBuilder();
            for (String e : p.errors) log.append(e).append('\n');
            List<Integer> shaders = new ArrayList<>();
            boolean compiled = true;
            for (int k = 0; k < 4; k++) {
                if (p.sources[k] == null) continue;
                String ext = new String[] {"vsh", "gsh", "fsh", "csh"}[k];
                Files.writeString(out.resolve(p.name + "." + ext + ".glsl"), p.sources[k], StandardCharsets.UTF_8);
                int sh = GL20.glCreateShader(new int[] {GL20.GL_VERTEX_SHADER, 0x8DD9, GL20.GL_FRAGMENT_SHADER, GL43.GL_COMPUTE_SHADER}[k]);
                GL20.glShaderSource(sh, p.sources[k]);
                GL20.glCompileShader(sh);
                if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                    compiled = false;
                    log.append(ext).append(" compile:\n").append(GL20.glGetShaderInfoLog(sh, 32768)).append('\n');
                }
                shaders.add(sh);
            }
            if (compiled && !shaders.isEmpty()) {
                int prog = GL20.glCreateProgram();
                for (int sh : shaders) GL20.glAttachShader(prog, sh);
                for (int a = 0; a < GlslTranslator.ATTRIBUTES.length; a++) GL20.glBindAttribLocation(prog, a, GlslTranslator.ATTRIBUTES[a][0]);
                GL20.glLinkProgram(prog);
                if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0) {
                    compiled = false;
                    log.append("link:\n").append(GL20.glGetProgramInfoLog(prog, 32768)).append('\n');
                }
                GL20.glDeleteProgram(prog);
            }
            for (int sh : shaders) GL20.glDeleteShader(sh);
            if (compiled) ok++;
            else failed.add(p.name);
            if (log.length() > 0) Files.writeString(out.resolve(p.name + ".log"), log.toString(), StandardCharsets.UTF_8);
        }
        StringBuilder table = new StringBuilder();
        for (String g : ProgramSet.FALLBACK.keySet()) {
            ProgramSet.Program p = set.resolve(g);
            table.append(String.format("  %-30s -> %-26s writes %s%n", g, p == null ? "(none: vanilla)" : p.name,
                    p == null ? "" : Arrays.toString(p.drawBuffers) + (p.explicitDrawBuffers ? "" : " (default)")));
        }
        for (ProgramSet.Program p : set.programs.values())
            if (!ProgramSet.FALLBACK.containsKey(p.name))
                table.append(String.format("  %-30s %s%s writes %s%n", p.name, p.computeOnly() ? "compute only" : "",
                        p.computes.isEmpty() ? "" : " + " + p.computes.size() + " compute", Arrays.toString(p.drawBuffers)));
        table.append("  consts: ").append(set.consts).append('\n');
        table.append("  properties: ").append(props.all.size()).append(" keys, ").append(props.customUniforms.size()).append(" custom uniforms")
                .append(props.errors.isEmpty() ? "" : ", errors " + props.errors).append('\n');
        Files.writeString(out.resolve("_programs.txt"), table.toString(), StandardCharsets.UTF_8);
        System.out.printf("[sleep/shaders] compile test %s (%s): %d options, %d programs, %d ok, %d failed %s; load %.0f ms, compile %.0f ms; %s%n%s",
                src.name, set.folder.isEmpty() ? "shaders/" : set.folder, options.options.size(), set.programs.size(), ok, failed.size(), failed,
                (tLoad - t0) / 1e6, (System.nanoTime() - tLoad) / 1e6, out, table);
    }
}
