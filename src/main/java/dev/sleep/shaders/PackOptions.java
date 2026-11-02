package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pack's options, found the way OptiFine / Iris find them: in every shader source file (all of shaders/, includes and
 * dimension folders), {@code #define NAME} / {@code //#define NAME} (boolean, used by an #ifdef / #ifndef / defined in
 * the same file), {@code #define NAME value // [v1 v2 ...]} (value), {@code const int|float NAME = v; // [v1 v2]}
 * (const). A changed value rewrites those lines before preprocessing (boolean: commented in / out; value: the value).
 * The same option with different defaults in different files is ambiguous and left alone.
 */
public final class PackOptions {
    public enum Kind { BOOL, VALUE, CONST }

    public static final class Option {
        public final String name;
        public final Kind kind;
        public final String def;
        public final List<String> values;
        boolean ambiguous;

        Option(String name, Kind kind, String def, List<String> values) {
            this.name = name;
            this.kind = kind;
            this.def = def;
            this.values = values;
        }
    }

    private static final Pattern BOOL = Pattern.compile("^\\s*(//\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(//.*)?$");
    private static final Pattern VALUE = Pattern.compile("^\\s*(//\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([^\\s/]+)\\s*//\\s*\\[([^\\]]*)\\].*$");
    private static final Pattern CONST = Pattern.compile("^\\s*const\\s+(int|float|bool)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+)\\s*;\\s*//\\s*\\[([^\\]]*)\\].*$");

    public final Map<String, Option> options = new LinkedHashMap<>();
    /** user values (shaderpacks/<pack>.txt): name -> value ("true"/"false" for booleans) */
    public final Map<String, String> set = new LinkedHashMap<>();

    public static PackOptions scan(ShaderPackSource src) {
        PackOptions o = new PackOptions();
        for (String f : src.list("")) {
            if (!(f.endsWith(".glsl") || f.endsWith(".vsh") || f.endsWith(".fsh") || f.endsWith(".gsh") || f.endsWith(".csh") || f.endsWith(".inc"))) continue;
            String text = src.text(f);
            if (text == null) continue;
            for (String line : text.split("\n")) {
                line = line.stripTrailing();
                Matcher m;
                if ((m = VALUE.matcher(line)).matches()) {
                    List<String> vals = new ArrayList<>(List.of(m.group(4).trim().split("\\s+")));
                    if (!vals.contains(m.group(3))) vals.add(0, m.group(3));
                    o.add(new Option(m.group(2), Kind.VALUE, m.group(3), vals));
                } else if ((m = CONST.matcher(line)).matches()) {
                    List<String> vals = new ArrayList<>(List.of(m.group(4).trim().split("\\s+")));
                    if (!vals.contains(m.group(3))) vals.add(0, m.group(3));
                    o.add(new Option(m.group(2), Kind.CONST, m.group(3), vals));
                } else if ((m = BOOL.matcher(line)).matches()) {
                    String name = m.group(2);
                    if (!testedIn(text, name)) continue;
                    o.add(new Option(name, Kind.BOOL, m.group(1) == null ? "true" : "false", List.of("true", "false")));
                }
            }
        }
        return o;
    }

    private static boolean testedIn(String text, String name) {
        return Pattern.compile("#\\s*if(n?def\\s+" + name + "\\b|.*\\bdefined\\s*\\(?\\s*" + name + "\\b)").matcher(text).find();
    }

    private void add(Option n) {
        Option old = options.get(n.name);
        if (old == null) options.put(n.name, n);
        else if (old.kind != n.kind || !old.def.equals(n.def)) old.ambiguous = true;
    }

    /** user values from a properties text (shaderpacks/<pack>.txt) */
    public void load(String text) {
        Properties p = new Properties();
        try {
            p.load(new java.io.StringReader(text));
        } catch (java.io.IOException ignored) {}
        for (String k : p.stringPropertyNames()) set.put(k, p.getProperty(k).trim());
    }

    /** the value in effect */
    public String value(String name) {
        Option o = options.get(name);
        if (o == null) return null;
        String v = o.ambiguous ? null : set.get(name);
        return v != null ? v : o.def;
    }

    /** a source line with the user's value applied (lines that are no option stay as they are) */
    public String rewrite(String line) {
        if (set.isEmpty() || line.indexOf('#') < 0 && !line.contains("const")) return line;
        Matcher m;
        if ((m = VALUE.matcher(line)).matches()) {
            Option o = options.get(m.group(2));
            String v = o == null || o.ambiguous ? null : set.get(o.name);
            if (v == null) return line;
            return line.substring(0, m.start(3)) + v + line.substring(m.end(3));
        }
        if ((m = CONST.matcher(line)).matches()) {
            Option o = options.get(m.group(2));
            String v = o == null || o.ambiguous ? null : set.get(o.name);
            if (v == null) return line;
            return line.substring(0, m.start(3)) + v + line.substring(m.end(3));
        }
        if ((m = BOOL.matcher(line)).matches()) {
            Option o = options.get(m.group(2));
            String v = o == null || o.ambiguous || o.kind != Kind.BOOL ? null : set.get(o.name);
            if (v == null) return line;
            boolean on = Boolean.parseBoolean(v), isOn = m.group(1) == null;
            if (on == isOn) return line;
            String indent = line.substring(0, line.length() - line.stripLeading().length());
            String rest = line.stripLeading();
            return on ? indent + rest.replaceFirst("^//\\s*", "") : indent + "//" + rest;
        }
        return line;
    }

    /** the options as macros (shaders.properties and the id .properties files are preprocessed with them) */
    public List<String> asMacros() {
        List<String> out = new ArrayList<>();
        for (Option o : options.values()) {
            String v = value(o.name);
            if (o.kind == Kind.BOOL) {
                if (Boolean.parseBoolean(v)) out.add(o.name);
            } else if (o.kind == Kind.VALUE) out.add(o.name + " " + v);
        }
        return out;
    }
}
