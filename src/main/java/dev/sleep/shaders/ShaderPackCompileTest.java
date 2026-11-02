package dev.sleep.shaders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

/**
 * -Dsleep.shaders.compileTest=<pack>: on the first frame, every program of the pack's overworld folder is
 * preprocessed (default option values), translated to core GLSL and compiled + linked; per program OK or the driver's
 * log. Sources as compiled and the logs go to run/sleep/shaders/<pack>/. -Dsleep.shaders.compileTest.quit=true quits.
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
        String world = src.list("world0").isEmpty() ? "" : "world0/";
        Map<String, String[]> programs = new TreeMap<>();   // name -> {vsh, gsh, fsh, csh}
        for (String f : src.list(world.isEmpty() ? "" : "world0")) {
            String rel = f.substring(world.length());
            if (rel.contains("/")) continue;
            int dot = rel.lastIndexOf('.');
            if (dot < 0) continue;
            String base = rel.substring(0, dot), ext = rel.substring(dot + 1);
            int k = switch (ext) {
                case "vsh" -> 0;
                case "gsh" -> 1;
                case "fsh" -> 2;
                case "csh" -> 3;
                default -> -1;
            };
            if (k >= 0) programs.computeIfAbsent(base, x -> new String[4])[k] = f;
        }
        List<String> macros = StandardMacros.build(List.of());
        Path out = gameDir.resolve("sleep/shaders/" + src.name);
        Files.createDirectories(out);
        int ok = 0, bad = 0;
        List<String> failed = new ArrayList<>();
        for (var e : programs.entrySet()) {
            String name = e.getKey();
            String[] files = e.getValue();
            StringBuilder log = new StringBuilder();
            List<Integer> shaders = new ArrayList<>();
            boolean compiled = true;
            for (int k = 0; k < 4; k++) {
                if (files[k] == null) continue;
                GlslTranslator.Stage stage = GlslTranslator.Stage.values()[k];
                GlslPreprocessor pp = new GlslPreprocessor(src::text, macros, l -> l);
                String pre = pp.process(files[k]);
                if (!pp.errors().isEmpty()) log.append(stage).append(" preprocessor: ").append(pp.errors()).append('\n');
                GlslTranslator.Result r = GlslTranslator.translate(pre, stage);
                if (!r.notes().isEmpty()) log.append(stage).append(" translator: ").append(r.notes()).append('\n');
                String ext = new String[] {"vsh", "gsh", "fsh", "csh"}[k];
                Files.writeString(out.resolve(name + "." + ext + ".glsl"), r.text(), StandardCharsets.UTF_8);
                int type = new int[] {GL20.GL_VERTEX_SHADER, 0x8DD9, GL20.GL_FRAGMENT_SHADER, GL43.GL_COMPUTE_SHADER}[k];
                int sh = GL20.glCreateShader(type);
                GL20.glShaderSource(sh, r.text());
                GL20.glCompileShader(sh);
                if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == 0) {
                    compiled = false;
                    log.append(stage).append(" compile:\n").append(GL20.glGetShaderInfoLog(sh, 32768)).append('\n');
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
            else {
                bad++;
                failed.add(name);
            }
            if (log.length() > 0) Files.writeString(out.resolve(name + ".log"), log.toString(), StandardCharsets.UTF_8);
        }
        System.out.printf("[sleep/shaders] compile test %s (%s): %d programs, %d ok, %d failed %s, %.0f ms; sources and logs in %s%n", src.name,
                world.isEmpty() ? "shaders/" : world, programs.size(), ok, bad, failed, (System.nanoTime() - t0) / 1e6, out);
    }
}
