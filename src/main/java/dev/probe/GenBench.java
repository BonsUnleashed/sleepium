package dev.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunk pipeline benchmark: force-load an N x N square of never-generated chunks far from spawn and measure
 * how long until every one is a FULL LevelChunk. Exercises the worldgen / light / sorter mailboxes, IO and the
 * chunk-source main executor — the cross-thread paths that stay idle in the static lag scenes.
 *
 * <p>{@code /probe genbench <side> <label>}: side x side chunks (e.g. 24 -> 576 chunks). Tick stats, event and task
 * counters run for the whole generation and are written as a normal report plus a row in genbench.csv.
 */
final class GenBench {
    private static ServerLevel level;
    private static int x0, z0, side, total;
    private static long startNanos;
    private static int startTick;
    private static String label = "";
    private static int runIndex;
    private static boolean active;

    private GenBench() {}

    static boolean active() {
        return active;
    }

    static void start(MinecraftServer server, int sideChunks, String l) {
        level = server.overworld();
        side = sideChunks;
        total = side * side;
        label = l;
        // A fresh region every run, far from spawn and from previous runs (1 run = 1 column of regions).
        x0 = 4000 + (runIndex++) * (side + 8);
        z0 = 4000;
        ProbeCommand.startRecording();
        startTick = TickTimer.INSTANCE.ticks();
        startNanos = System.nanoTime();
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                // Async FORCED ticket (ticket level 31). NOT level.setChunkForced(): that also calls getChunk()
                // synchronously, i.e. generates one chunk at a time on the server thread (the /forceload command
                // does the same and freezes the server: 256 chunks = 7.6 s in one tick).
                level.getChunkSource().updateChunkForced(new ChunkPos(x0 + dx, z0 + dz), true);
            }
        }
        active = true;
        ProbeMod.LOGGER.info("probe: genbench '{}' started, {} chunks at chunk {},{}", label, total, x0, z0);
    }

    /** Server thread, end of every tick. */
    static void onTickEnd(MinecraftServer server) {
        if (!active) {
            return;
        }
        int done = 0;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                if (level.getChunkSource().getChunkNow(x0 + dx, z0 + dz) != null) {
                    done++;
                }
            }
        }
        double secs = (System.nanoTime() - startNanos) / 1e9;
        if (done < total && secs < 600) {
            return;
        }
        active = false;
        int ticks = TickTimer.INSTANCE.ticks() - startTick;
        String line = String.format(Locale.ROOT, "genbench '%s': %d/%d chunks FULL in %.2f s (%d ticks) = %.1f chunks/s",
                label, done, total, secs, ticks, done / secs);
        Path report = ProbeCommand.finishRecording(server, "genbench-" + label, line);
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                level.getChunkSource().updateChunkForced(new ChunkPos(x0 + dx, z0 + dz), false);
            }
        }
        Path csv = server.getServerDirectory().resolve("probe").resolve("genbench.csv");
        try {
            if (!Files.exists(csv)) {
                Files.writeString(csv, "time,label,chunks,seconds,chunks_per_s,ticks\n");
            }
            Files.writeString(csv, String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.2f,%d%n",
                    LocalDateTime.now().withNano(0), label, done, secs, done / secs, ticks), StandardOpenOption.APPEND);
        } catch (IOException e) {
            ProbeMod.LOGGER.error("Cannot append {}", csv, e);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("probe: " + line), false);
        ProbeMod.LOGGER.info("probe: {} -> {}", line, report);
    }
}
