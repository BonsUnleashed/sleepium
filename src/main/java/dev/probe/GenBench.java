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
    /** Hash of each chunk taken at ChunkEvent.Load (end of the FULL step), before post-processing or any tick. */
    private static final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap loadHashes = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

    static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load e) {
        if (!active || e.getLevel() != level) return;
        ChunkPos p = e.getChunk().getPos();
        if (p.x < x0 || p.x >= x0 + side || p.z < z0 || p.z >= z0 + side) return;
        loadHashes.put(p.toLong(), hashChunk(e.getChunk()));
    }

    static long hashChunk(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        long h = 1469598103934665603L;   // FNV-1a over block state ids, fixed order
        for (var section : chunk.getSections()) {
            if (section.hasOnlyAir()) {
                h = (h ^ 0x9E37) * 1099511628211L;
                continue;
            }
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        int id = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(section.getBlockState(x, y, z));
                        h = (h ^ id) * 1099511628211L;
                    }
        }
        return h;
    }

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
        loadHashes.clear();
        GenBenchHooks.CARVERS.clear();
        GenBenchHooks.x0 = x0;
        GenBenchHooks.z0 = z0;
        GenBenchHooks.side = side;
        GenBenchHooks.active = true;
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

    /**
     * Content hash of every generated chunk (all block states of all sections, in a fixed order) to
     * run/probe/genhash-<label>.txt as "cx cz hash". Used to check that parallel generation produces the same world.
     */
    private static String writeChunkHashes(MinecraftServer server) {
        StringBuilder out = new StringBuilder();
        long all = 1469598103934665603L;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                long h = loadHashes.getOrDefault(ChunkPos.asLong(x0 + dx, z0 + dz), 0L);   // 0 = no Load seen
                out.append(x0 + dx).append(' ').append(z0 + dz).append(' ').append(Long.toHexString(h)).append('\n');
                all = (all ^ h) * 1099511628211L;
            }
        }
        StringBuilder carv = new StringBuilder();
        for (int dx = 0; dx < side; dx++)
            for (int dz = 0; dz < side; dz++) {
                Long c = GenBenchHooks.CARVERS.get(ChunkPos.asLong(x0 + dx, z0 + dz));
                carv.append(x0 + dx).append(' ').append(z0 + dz).append(' ').append(c == null ? "0" : Long.toHexString(c)).append('\n');
            }
        try {
            Files.writeString(server.getServerDirectory().resolve("probe").resolve("genhash-" + label + "-carvers.txt"), carv.toString());
        } catch (IOException e) {
            ProbeMod.LOGGER.error("Cannot write carvers hashes", e);
        }
        Path f = server.getServerDirectory().resolve("probe").resolve("genhash-" + label + ".txt");
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, out.toString());
        } catch (IOException e) {
            ProbeMod.LOGGER.error("Cannot write {}", f, e);
        }
        String par = dev.probe.optsupport.ParallelGen.ENABLED
                ? String.format(java.util.Locale.ROOT, "; parallel worldgen: offloaded %d steps, %d feature jobs, %d contended locks",
                        dev.probe.optsupport.ParallelGen.OFFLOADED.sumThenReset(), dev.probe.optsupport.ParallelGen.FEATURE_JOBS.sumThenReset(),
                        dev.probe.optsupport.ParallelGen.LOCK_CONTENDED.sumThenReset())
                : "";
        return "world hash " + Long.toHexString(all) + par + " /" + " (" + total + " chunks, per-chunk hashes in " + f.getFileName() + ")";
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
        GenBenchHooks.active = false;
        int ticks = TickTimer.INSTANCE.ticks() - startTick;
        String line = String.format(Locale.ROOT, "genbench '%s': %d/%d chunks FULL in %.2f s (%d ticks) = %.1f chunks/s",
                label, done, total, secs, ticks, done / secs);
        String hashLine = writeChunkHashes(server);
        Path report = ProbeCommand.finishRecording(server, "genbench-" + label, line + "\n" + hashLine);
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
