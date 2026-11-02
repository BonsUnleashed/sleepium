package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * shaders.properties after the C preprocessor (standard macros + the options as macros): Java-properties lines
 * ({@code key = value}, leading whitespace ignored, '\' continuation). Unknown keys are kept and ignored. The keys this
 * engine reads are accessed by name; {@code uniform.* / variable.*} keep their declaration order (later ones may use
 * earlier ones).
 */
public final class PackProperties {
    public final Map<String, String> all = new LinkedHashMap<>();
    /** uniform.<type>.<name> / variable.<type>.<name> in file order: {"uniform"|"variable", type, name, expr} */
    public final List<String[]> customUniforms = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();

    public static PackProperties load(ShaderPackSource src, List<String> macros) {
        PackProperties p = new PackProperties();
        String text = src.text("shaders.properties");
        if (text == null) return p;
        GlslPreprocessor pp = new GlslPreprocessor(src::text, macros, l -> l);
        String pre = pp.process("shaders.properties");
        p.errors.addAll(pp.errors());
        String[] lines = pre.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip();
            while (l.endsWith("\\") && i + 1 < lines.length) l = l.substring(0, l.length() - 1) + lines[++i].strip();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("!") && !l.contains("=")) continue;
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            String k = l.substring(0, eq).strip(), v = l.substring(eq + 1).strip();
            p.all.put(k, v);
            if (k.startsWith("uniform.") || k.startsWith("variable.")) {
                String[] parts = k.split("\\.", 3);
                if (parts.length == 3) p.customUniforms.add(new String[] {parts[0], parts[1], parts[2], v});
            }
        }
        return p;
    }

    public String get(String key) {
        return all.get(key);
    }

    public String get(String key, String def) {
        String v = all.get(key);
        return v == null ? def : v;
    }

    public boolean bool(String key, boolean def) {
        String v = all.get(key);
        return v == null ? def : v.equalsIgnoreCase("true");
    }

    /**
     * program.<[dim/]name>.enabled: a boolean expression over boolean options ({@code !}, {@code &&}, {@code ||},
     * parentheses, true / false), evaluated with the C preprocessor's #if evaluator - each option name becomes
     * {@code defined(NAME)}.
     */
    public boolean programEnabled(String dimPrefix, String program, List<String> optionMacros) {
        String e = all.get("program." + dimPrefix + program + ".enabled");
        if (e == null) e = all.get("program." + program + ".enabled");
        if (e == null) return true;
        String expr = e.replaceAll("\\b(?!true\\b|false\\b|defined\\b)([A-Za-z_][A-Za-z0-9_]*)\\b", "defined($1)").replace("true", "1").replace("false", "0");
        GlslPreprocessor pp = new GlslPreprocessor(path -> path.equals("x") ? "#if " + expr + "\nyes\n#endif\n" : null, optionMacros, l -> l);
        return pp.process("x").contains("yes");
    }
}
