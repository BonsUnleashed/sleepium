package dev.sleep.optsupport;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.management.ObjectName;

/**
 * lab.opt.ClassPreload: classes the game loads only while it is being played - the first mob, block entity or
 * structure of a kind - are learned per installation and loaded on a background thread at the next server start, so
 * their loading (through every transformer: ~0.7 ms a class here) leaves the server tick. 10-08, singleplayer
 * teleports: 345 classes loaded after joining (mostly mob AI behaviours); with them preloaded the ticks with 50 ms or
 * more of work went 16 -> 10, the worst 152 / 101 / 100 / 97 -> 132 / 103 / 63 / 53 ms.
 *
 * <p>Learning: the JVM's own list of loaded classes (the VM.class_hierarchy diagnostic command, in-process) when the
 * server has started and when it stops; what is new at the stop is added to {@code <game dir>/probe/preload-classes.txt}
 * (union over sessions, at most 20 000 names). Preloading: {@code Class.forName(name, false, loader)} - loaded and
 * linked, NOT initialised: static initialisers still run at first use, in the order they would anyway, so nothing the
 * game does changes. Hidden and lambda classes are not listed; names that no longer load are skipped.
 * {@code -Dlab.preload.file=<list>} preloads a given list instead (experiments).
 */
public final class ClassPreload {
    public static final boolean ON = Boolean.getBoolean("lab.opt.ClassPreload") || System.getProperty("lab.preload.file") != null;
    private static final int MAX = 20_000;
    private static volatile Set<String> atStart;

    private ClassPreload() {}

    private static Path file() {
        String f = System.getProperty("lab.preload.file");
        return f != null ? Path.of(f) : net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("probe").resolve("preload-classes.txt");
    }

    /** ServerAboutToStartEvent: the learned classes, loaded in the background while the server starts */
    public static void preload() {
        if (!ON) return;
        Path f = file();
        if (!Files.exists(f)) return;
        ClassLoader loader = net.minecraft.server.MinecraftServer.class.getClassLoader();
        Thread t = new Thread(() -> {
            long t0 = System.nanoTime();
            int ok = 0, missing = 0;
            try {
                for (String n : Files.readAllLines(f)) {
                    if (n.isBlank()) continue;
                    try {
                        Class.forName(n.trim(), false, loader);
                        ok++;
                    } catch (Throwable e) {
                        missing++;
                    }
                }
            } catch (Exception e) {
                System.out.println("[probe/preload] " + e);
            }
            System.out.printf("[probe/preload] %d classes loaded in the background, %d skipped, %.0f ms%n", ok, missing, (System.nanoTime() - t0) / 1e6);
        }, "probe-class-preload");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** ServerStartedEvent: what is loaded once the world is up (loading itself is not "while playing") */
    public static void started() {
        if (!ON || System.getProperty("lab.preload.file") != null) return;
        Thread t = new Thread(() -> atStart = loaded(), "probe-class-snapshot");
        t.setDaemon(true);
        t.start();
    }

    /** ServerStoppedEvent: what was loaded while playing joins the list */
    public static void stopped() {
        Set<String> start = atStart;
        if (!ON || start == null) return;
        atStart = null;
        try {
            Set<String> now = loaded();
            now.removeAll(start);
            Path f = file();
            LinkedHashSet<String> all = new LinkedHashSet<>();
            if (Files.exists(f)) for (String n : Files.readAllLines(f)) if (!n.isBlank()) all.add(n.trim());
            int before = all.size();
            all.addAll(now);
            List<String> out = new ArrayList<>(all);
            if (out.size() > MAX) out = out.subList(out.size() - MAX, out.size());
            Files.createDirectories(f.getParent());
            Files.write(f, out);
            System.out.printf("[probe/preload] learned %d classes loaded while playing, %d new; list %d%n", now.size(), all.size() - before, out.size());
        } catch (Exception e) {
            System.out.println("[probe/preload] not learned: " + e);
        }
    }

    /** the names of every loaded class but hidden / lambda ones, from the JVM's class hierarchy */
    static Set<String> loaded() {
        Set<String> names = new HashSet<>();
        try {
            String out = (String) ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "vmClassHierarchy", new Object[] {new String[0]}, new String[] {String[].class.getName()});
            for (String line : out.split("\n")) {
                int i = line.lastIndexOf("--");
                String s = (i >= 0 ? line.substring(i + 2) : line).trim();
                int sp = s.indexOf(' ');
                if (sp >= 0) s = s.substring(0, sp);   // " (intf)"
                String[] parts = s.split("/");
                if (parts.length != 2) continue;       // hidden classes: name/0x.../loader
                String n = parts[0];
                if (n.isEmpty() || n.contains("$$") || n.startsWith("[")) continue;
                names.add(n);
            }
        } catch (Exception e) {
            System.out.println("[probe/preload] class list unavailable: " + e);
        }
        return names;
    }
}
