package dev.lull.optsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The player's settings (the settings screen): config/sleepium.properties, {@code key=value} per engine flag the player
 * changed. Applied by GameDefaults before the defaults and before any flag is read: a launch flag ({@code -Dkey=...})
 * still wins over the file, the file over the defaults. Most flags are read once at start (mixins apply when their
 * class loads): the screen says when a change needs a restart.
 *
 * <p>The file documents itself: on the first start (a dedicated server too: it has no settings screen) and on every save
 * it lists every switch of the catalogue (options.tsv) by group with its description and default, commented out unless
 * set - a server owner edits it like any config. An existing file is only rewritten by the screen's save.
 */
public final class SleepConfig {
    private SleepConfig() {}

    private static final Map<String, String> FILE = new TreeMap<>();
    /** engine keys given on the command line (the screen shows them locked) */
    private static final Set<String> LAUNCH = new HashSet<>();
    /** the value of each engine key when the game started (a difference needs a restart) */
    private static final Map<String, String> STARTUP = new TreeMap<>();
    private static boolean loaded;

    public static Path file() {
        Path dir;
        try {
            dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            dir = Path.of("config");
        }
        return dir.resolve("sleepium.properties");
    }

    static boolean engineKey(String k) {
        return k.startsWith("lab.") || k.startsWith("sleep.");
    }

    /** GameDefaults, first: remembers the launch flags, then sets the file's values that no launch flag gives */
    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (String k : System.getProperties().stringPropertyNames()) if (engineKey(k)) LAUNCH.add(k);
        Path f = file();
        if (!Files.exists(f)) {
            write(f, Map.of());   // the documented catalogue, every line commented: the defaults stay the jar's
            return;
        }
        try {
            int set = 0;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq < 0) continue;
                String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
                if (!engineKey(k)) continue;
                FILE.put(k, v);
                if (!LAUNCH.contains(k)) {
                    System.setProperty(k, v);
                    set++;
                }
            }
            System.out.println("[sleep/opt] settings: " + set + " from " + f + (FILE.size() > set ? " (" + (FILE.size() - set) + " overridden by launch flags)" : ""));
        } catch (IOException e) {
            System.out.println("[sleep/opt] settings: " + f + ": " + e);
        }
    }

    /** GameDefaults, last: the values the game starts with */
    static synchronized void started() {
        for (String k : System.getProperties().stringPropertyNames()) if (engineKey(k)) STARTUP.put(k, System.getProperty(k));
    }

    public static boolean launchFlag(String key) {
        return LAUNCH.contains(key);
    }

    /** the value the game started with (null: unset) */
    public static String startup(String key) {
        return STARTUP.get(key);
    }

    /** the file's values (a copy) */
    public static synchronized Map<String, String> saved() {
        return new TreeMap<>(FILE);
    }

    /** replaces the file's values (the screen's Done); also sets them as properties for what reads them live */
    public static synchronized void save(Map<String, String> values) {
        FILE.clear();
        FILE.putAll(values);
        for (var e : FILE.entrySet()) if (!LAUNCH.contains(e.getKey())) System.setProperty(e.getKey(), e.getValue());
        write(file(), FILE);
    }

    /**
     * The file with every switch of the catalogue, by group: its description, then {@code key=value} when set here, else
     * the line commented out with its default; keys set here that the catalogue does not know at the end.
     */
    private static void write(Path f, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Sleepium settings\n");
        sb.append("#\n");
        sb.append("# Every switch of Sleepium with what it does and its default. A line key=value here wins over the default;\n");
        sb.append("# a line starting with # uses the default (so a new version's defaults reach you). A -Dkey=value launch flag\n");
        sb.append("# wins over this file. The in-game settings screen (Options -> Sleepium...) writes this file too.\n");
        sb.append("# Most switches take effect after a restart (a server: edit, then restart it). true = on, false = off.\n");
        sb.append("# Switches marked \"part of\" belong to another one: switching that one off switches them off too.\n");
        Map<String, String> groups = groupNames();
        Map<String, java.util.List<dev.lull.ui.SleepOptions.Option>> byGroup = dev.lull.ui.SleepOptions.byGroup();
        Set<String> known = new HashSet<>();
        for (var g : byGroup.entrySet()) {
            if (g.getValue().isEmpty()) continue;
            sb.append("\n# ==== ").append(groups.getOrDefault(g.getKey(), g.getKey())).append(" ====\n");
            String gd = groups.get(g.getKey() + ".desc");
            if (gd != null) sb.append("# ").append(gd).append('\n');
            for (var o : g.getValue()) {
                known.add(o.key());
                sb.append("\n# ").append(o.description().isEmpty() ? o.shortName() : o.description()).append('\n');
                if (!o.partOf().isEmpty()) sb.append("# (part of ").append(String.join(", ", o.partOf())).append(")\n");
                String v = values.get(o.key());
                if (v != null) sb.append(o.key()).append('=').append(v).append('\n');
                else sb.append('#').append(o.key()).append('=').append(o.byDefault()).append('\n');
            }
        }
        boolean other = false;
        for (var e : values.entrySet()) {
            if (known.contains(e.getKey())) continue;
            if (!other) sb.append("\n# ==== Other settings ====\n");
            other = true;
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, sb, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            System.out.println("[sleep/opt] settings: could not write " + f + ": " + e);
        }
    }

    /** the groups' names and descriptions as the screen shows them (en_us.json: sleepium.ui.group.<g>[.desc]) */
    private static Map<String, String> groupNames() {
        Map<String, String> m = new TreeMap<>();
        try (java.io.InputStream in = SleepConfig.class.getResourceAsStream("/assets/sleepium/lang/en_us.json")) {
            if (in == null) return m;
            java.util.regex.Matcher x = java.util.regex.Pattern.compile("\"sleepium\\.ui\\.group\\.([\\w.]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (x.find()) m.put(x.group(1), x.group(2).replace("\\\"", "\""));
        } catch (IOException e) {
            // the keys stand in for the names
        }
        return m;
    }
}
