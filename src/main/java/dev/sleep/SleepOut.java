package dev.sleep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the engine's measurements go: the game directory's ROOT folder ({@code -Dsleep.outDir}, "sleep" by default),
 * and a test's pictures in ROOT/KIND/LABEL/, one folder a run's label (50 000 shots in one folder had grown to 22 GB).
 * The first write of a label in a session empties its folder - a label run again replaces its pictures, it does not
 * add to them.
 */
public final class SleepOut {
    /** the folder under the game directory the engine writes its reports, hashes and pictures to */
    public static final String ROOT = System.getProperty("sleep.outDir", "sleep");

    private static final Set<String> FRESH = ConcurrentHashMap.newKeySet();

    private SleepOut() {}

    /** game/ROOT/kind/label, made (and emptied the first time this session) */
    public static Path dir(Path game, String kind, String label) throws IOException {
        Path d = game.resolve(ROOT).resolve(kind).resolve(safe(label));
        if (FRESH.add(kind + "/" + label) && Files.isDirectory(d)) {
            try (var s = Files.list(d)) {
                for (Path f : (Iterable<Path>) s::iterator) if (Files.isRegularFile(f)) Files.deleteIfExists(f);
            }
        }
        Files.createDirectories(d);
        return d;
    }

    private static String safe(String label) {
        return label.replaceAll("[^A-Za-z0-9_.@+-]", "_");
    }
}
