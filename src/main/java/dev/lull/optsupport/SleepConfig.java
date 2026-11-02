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
        if (!Files.exists(f)) return;
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
        StringBuilder sb = new StringBuilder("# Sleepium settings (written by the settings screen; a -D launch flag wins over a line here)\n");
        for (var e : FILE.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            if (!LAUNCH.contains(e.getKey())) System.setProperty(e.getKey(), e.getValue());
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), sb, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[sleep/opt] settings: could not write " + file() + ": " + e);
        }
    }
}
