package dev.lull.shaders;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A shaderpack's files (OptiFine / Iris format): a folder or a .zip in {@code shaderpacks/}, everything below its
 * {@code shaders/} directory (at the root or one level down). Read once into memory: a pack is a few MB of text, and
 * the preprocessor asks for the same includes many times.
 *
 * <p>Paths are relative to {@code shaders/}, '/' separated, no leading slash ({@code program/gbuffers_terrain.glsl}).
 */
public final class ShaderPackSource {
    public final String name;
    private final Map<String, byte[]> files;

    private ShaderPackSource(String name, Map<String, byte[]> files) {
        this.name = name;
        this.files = files;
    }

    /** the pack {@code name} (a folder, or a zip with or without ".zip") in {@code dir} */
    public static ShaderPackSource open(Path dir, String name) throws IOException {
        Path p = dir.resolve(name);
        if (!Files.exists(p) && !name.endsWith(".zip")) p = dir.resolve(name + ".zip");
        if (!Files.exists(p)) throw new IOException("no shaderpack " + name + " in " + dir);
        Map<String, byte[]> all = new HashMap<>();
        if (Files.isDirectory(p)) {
            Path root = p;
            try (var walk = Files.walk(root)) {
                for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator)
                    all.put(root.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
        } else {
            try (ZipFile z = new ZipFile(p.toFile())) {
                for (ZipEntry e : Collections.list(z.entries())) {
                    if (e.isDirectory()) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        all.put(e.getName().replace('\\', '/'), in.readAllBytes());
                    }
                }
            }
        }
        // shaders/ at the root, or one level down (a zip of the pack's folder)
        String prefix = null;
        for (String k : all.keySet()) {
            int i = k.indexOf("shaders/");
            if (i < 0 || (i > 0 && k.charAt(i - 1) != '/')) continue;
            String pre = k.substring(0, i + "shaders/".length());
            if (pre.chars().filter(c -> c == '/').count() > 2) continue;
            if (prefix == null || pre.length() < prefix.length()) prefix = pre;
        }
        if (prefix == null) throw new IOException("shaderpack " + name + " has no shaders/ directory");
        Map<String, byte[]> files = new HashMap<>();
        for (var e : all.entrySet()) if (e.getKey().startsWith(prefix)) files.put(e.getKey().substring(prefix.length()), e.getValue());
        return new ShaderPackSource(p.getFileName().toString(), files);
    }

    public boolean exists(String path) {
        return files.containsKey(normalize(path));
    }

    /** the file's text (UTF-8), or null */
    public String text(String path) {
        byte[] b = files.get(normalize(path));
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    public byte[] bytes(String path) {
        return files.get(normalize(path));
    }

    /** every file below {@code dir} ("" = all), sorted */
    public List<String> list(String dir) {
        String d = dir.isEmpty() || dir.endsWith("/") ? dir : dir + "/";
        List<String> out = new ArrayList<>();
        for (String k : files.keySet()) if (k.startsWith(d)) out.add(k);
        Collections.sort(out);
        return out;
    }

    /** "a/./b/../c" -> "a/c", no leading slash */
    public static String normalize(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        List<String> out = new ArrayList<>();
        for (String s : parts) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else out.add(s);
        }
        return String.join("/", out);
    }

    /** the directory part of a path ("program/x.glsl" -> "program") */
    public static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }
}
