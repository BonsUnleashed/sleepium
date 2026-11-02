package dev.lull.shaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * A shaderpack's own settings, for the pack options screen: its options (PackOptions, with the player's values from
 * shaderpacks/<pack>.txt - Iris' file and format), its screens, sliders, columns and profiles (shaders.properties),
 * their labels and comments in the game's language (lang/*.lang, English when missing). Saving writes the values
 * that differ from the pack's defaults.
 */
public final class PackScreenModel {
    public final String pack;
    private final PackOptions options;
    private final Map<String, String> lang = new HashMap<>();
    /** screen id ("" the root) -> its entries: an option, [SCREEN], <empty>, <profile>, * */
    public final Map<String, List<String>> screens = new LinkedHashMap<>();
    private final Map<String, Integer> columns = new HashMap<>();
    private final Set<String> sliders = new LinkedHashSet<>();
    /** profile name -> its settings ("OPT=v", "OPT" true, "!OPT" false, "profile.OTHER") */
    public final Map<String, List<String>> profiles = new LinkedHashMap<>();
    private final Path userFile;

    private PackScreenModel(String pack, PackOptions options, Path userFile) {
        this.pack = pack;
        this.options = options;
        this.userFile = userFile;
    }

    public static PackScreenModel load(Path gameDir, String name, String language) throws IOException {
        Path dir = gameDir.resolve("shaderpacks");
        ShaderPackSource src = ShaderPackSource.open(dir, name);
        PackOptions options = PackOptions.scan(src);
        Path userFile = dir.resolve(src.name + ".txt");
        if (Files.exists(userFile)) options.load(Files.readString(userFile));
        PackScreenModel m = new PackScreenModel(src.name, options, userFile);
        List<String> macros = new ArrayList<>(StandardMacros.build(List.of()));
        macros.addAll(options.asMacros());
        PackProperties props = PackProperties.load(src, macros);
        for (var e : props.all.entrySet()) {
            String k = e.getKey(), v = e.getValue().trim();
            if (k.equals("screen")) m.screens.put("", List.of(v.split("\\s+")));
            else if (k.startsWith("screen.") && k.endsWith(".columns")) m.columns.put(k.substring(7, k.length() - 8), parse(v));
            else if (k.equals("screen.columns")) m.columns.put("", parse(v));
            else if (k.startsWith("screen.")) m.screens.put(k.substring(7), List.of(v.split("\\s+")));
            else if (k.equals("sliders")) m.sliders.addAll(List.of(v.split("\\s+")));
            else if (k.startsWith("profile.")) m.profiles.put(k.substring(8), List.of(v.split("\\s+")));
        }
        if (!m.screens.containsKey("")) m.screens.put("", List.of("*"));
        // labels: the game's language, then English
        for (String l : new String[] {"en_US", "en_us", language, upper(language)}) {
            String text = src.text("lang/" + l + ".lang");
            if (text == null) continue;
            Properties p = new Properties();
            p.load(new java.io.StringReader(text));
            for (String k : p.stringPropertyNames()) m.lang.put(k, p.getProperty(k));
        }
        return m;
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static String upper(String lang) {
        int u = lang.indexOf('_');
        return u < 0 ? lang : lang.substring(0, u) + "_" + lang.substring(u + 1).toUpperCase(java.util.Locale.ROOT);
    }

    public int columns(String screen) {
        return columns.getOrDefault(screen, 2);
    }

    public boolean isOption(String name) {
        return options.options.containsKey(name);
    }

    public boolean isSlider(String name) {
        return sliders.contains(name);
    }

    public boolean isBool(String name) {
        PackOptions.Option o = options.options.get(name);
        return o != null && o.kind == PackOptions.Kind.BOOL;
    }

    public List<String> values(String name) {
        PackOptions.Option o = options.options.get(name);
        return o == null ? List.of() : o.values;
    }

    public String value(String name) {
        return options.value(name);
    }

    public String defaultValue(String name) {
        PackOptions.Option o = options.options.get(name);
        return o == null ? null : o.def;
    }

    public void set(String name, String value) {
        PackOptions.Option o = options.options.get(name);
        if (o == null) return;
        if (value.equals(o.def)) options.set.remove(name);
        else options.set.put(name, value);
    }

    /** the options no screen names (a "*" entry shows them) */
    public List<String> unplaced() {
        Set<String> placed = new java.util.HashSet<>();
        for (List<String> s : screens.values()) placed.addAll(s);
        List<String> out = new ArrayList<>();
        for (String n : options.options.keySet()) if (!placed.contains(n)) out.add(n);
        return out;
    }

    public String label(String option) {
        return lang.getOrDefault("option." + option, option);
    }

    public String comment(String option) {
        return lang.get("option." + option + ".comment");
    }

    public String valueLabel(String option, String value) {
        String l = lang.get("value." + option + "." + value);
        if (l != null) return l;
        if (isBool(option)) return null;
        String suffix = lang.get("suffix." + option);
        return suffix == null ? value : value + suffix;
    }

    public String screenLabel(String screen) {
        return screen.isEmpty() ? pack : lang.getOrDefault("screen." + screen, screen);
    }

    public String screenComment(String screen) {
        return lang.get("screen." + screen + ".comment");
    }

    public String profileLabel(String profile) {
        return profile == null ? lang.getOrDefault("profile.custom", "Custom") : lang.getOrDefault("profile." + profile, profile);
    }

    public String profileComment() {
        return lang.get("profile.comment");
    }

    /** the settings a profile makes, inherited ones first */
    private Map<String, String> profileSettings(String profile, int depth) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> tokens = profiles.get(profile);
        if (tokens == null || depth > 8) return out;
        for (String t : tokens) {
            if (t.startsWith("profile.")) out.putAll(profileSettings(t.substring(8), depth + 1));
            else if (t.startsWith("!")) out.put(t.substring(1), "false");
            else if (t.contains("=")) out.put(t.substring(0, t.indexOf('=')), t.substring(t.indexOf('=') + 1));
            else out.put(t, "true");
        }
        return out;
    }

    /** the profile the current values match (null: a custom mix) */
    public String profile() {
        outer:
        for (String p : profiles.keySet()) {
            for (var e : profileSettings(p, 0).entrySet()) if (isOption(e.getKey()) && !e.getValue().equals(value(e.getKey()))) continue outer;
            return p;
        }
        return null;
    }

    public void applyProfile(String profile) {
        for (var e : profileSettings(profile, 0).entrySet()) set(e.getKey(), e.getValue());
    }

    public List<String> profileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    public void resetAll() {
        options.set.clear();
    }

    /** shaderpacks/<pack>.txt: the values that differ from the pack's defaults */
    public void save() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (var e : new TreeMap<>(options.set).entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        Files.writeString(userFile, sb, StandardCharsets.UTF_8);
    }
}
