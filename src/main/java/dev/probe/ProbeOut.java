package dev.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a test's pictures go (10-20, the user: run/probe had grown to 50 000 files, 22 GB, all in one folder - the
 * recorder's frames and the tests' shots of every run since September): probe/KIND/LABEL/, one folder a run's label.
 * The first write of a label in a session empties its folder - a label run again replaces its pictures, it does not
 * add to them. The names inside are the old ones (frame-LABEL-i.png, rec-NAME-n-ms.png): tools/probe_out.py finds both.
 */
public final class ProbeOut {
    private static final Set<String> FRESH = ConcurrentHashMap.newKeySet();

    private ProbeOut() {}

    /** game/probe/kind/label, made (and emptied the first time this session) */
    public static Path dir(Path game, String kind, String label) throws IOException {
        Path d = game.resolve("probe").resolve(kind).resolve(safe(label));
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
