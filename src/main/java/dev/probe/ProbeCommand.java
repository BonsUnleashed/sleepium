package dev.probe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

final class ProbeCommand {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProbeCommand() {}

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("probe")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("start").executes(ProbeCommand::start))
                .then(Commands.literal("stop").executes(ProbeCommand::stop))
                .then(Commands.literal("run")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "seconds"),
                                                StringArgumentType.getString(ctx, "label"))))))
                .then(Commands.literal("genbench")
                        .then(Commands.argument("side", IntegerArgumentType.integer(2, 64))
                                .then(Commands.argument("label", StringArgumentType.word()).executes(ctx -> {
                                    GenBench.start(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "side"),
                                            StringArgumentType.getString(ctx, "label"));
                                    say(ctx, "probe: genbench started");
                                    return 1;
                                }))))
                .then(Commands.literal("busbench").executes(ctx -> {
                    if (EventCounter.INSTANCE.enabled()) {
                        say(ctx, "probe: run /probe stop first");
                        return 0;
                    }
                    try {
                        List<String> fails = new java.util.ArrayList<>(BusEquivalence.check(NeoForge.EVENT_BUS));
                        fails.addAll(BusAnatomy.sinkEquivalence(NeoForge.EVENT_BUS));
                        String eq = fails.isEmpty()
                                ? "equivalence: OK (" + BusEquivalence.expected() + "; per-listener sink counts match)\n"
                                : "equivalence: FAILED\n  " + String.join("\n  ", fails) + "\n";
                        String out = "# bus anatomy (in-game, server thread, best of 5)\n" + jvmLine() + "\n" + eq + "\n"
                                + BusAnatomy.format(BusAnatomy.run(400_000));
                        Path file = write(ctx.getSource().getServer(), "busbench", out);
                        say(ctx, out);
                        say(ctx, "probe: written to " + file);
                    } catch (ReflectiveOperationException e) {
                        say(ctx, "probe: busbench failed: " + e);
                    }
                    return 1;
                }))
                .then(Commands.literal("threads").executes(ctx -> {
                    // live threads by group + highest index seen per group + JVM-wide started count
                    var mx = ManagementFactory.getThreadMXBean();
                    java.util.TreeMap<String, int[]> g = new java.util.TreeMap<>();
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        String n = t.getName();
                        var m = java.util.regex.Pattern.compile("^(.*?)[-# ]?(\\d+)$").matcher(n);
                        String key = m.matches() ? m.group(1) : n;
                        int idx = m.matches() ? Integer.parseInt(m.group(2)) : 0;
                        int[] v = g.computeIfAbsent(key, k -> new int[2]);
                        v[0]++;
                        v[1] = Math.max(v[1], idx);
                    }
                    StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                            "threads: live %d, peak %d, total started since JVM start %d%n", mx.getThreadCount(), mx.getPeakThreadCount(), mx.getTotalStartedThreadCount()));
                    g.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "  %-44s live %3d   highest index %4d%n", k, v[0], v[1])));
                    ProbeMod.LOGGER.info("\n{}", sb);
                    say(ctx, sb.toString());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ProbeCommand::status))
                .then(Commands.literal("betypes").executes(ctx -> {
                    // per block-entity type: ticks, time, share of idle / sleepable ticks since the previous call
                    String r = BeTickStats.report(ctx.getSource().getServer());
                    ProbeMod.LOGGER.info("{}", r);
                    say(ctx, r);
                    return 1;
                }))
                .then(Commands.literal("events").executes(ctx -> {
                    // per-tick rates of every event mechanism since the previous call (vanilla + our bus)
                    String r = EventSources.report(ctx.getSource().getServer());
                    ProbeMod.LOGGER.info("\n{}", r);
                    say(ctx, r);
                    return 1;
                }))
                .then(Commands.literal("world").executes(ctx -> {
                    say(ctx, worldStats(ctx.getSource().getServer()));
                    return 1;
                }))
                .then(Commands.literal("bench")
                        .executes(ctx -> bench(ctx, 2_000_000))
                        .then(Commands.argument("n", IntegerArgumentType.integer(10_000, 100_000_000))
                                .executes(ctx -> bench(ctx, IntegerArgumentType.getInteger(ctx, "n"))))));
    }

    static void startRecording() {
        TickTimer.INSTANCE.reset();
        TaskStats.reset();
        JvmStats.start();
        EventCounter.INSTANCE.start();
    }

    /** Stops recording and writes report-<label>; {@code headline} goes on top of the report. */
    static Path finishRecording(MinecraftServer server, String label, String headline) {
        EventCounter.INSTANCE.stop();
        Path file = write(server, "report-" + label, headline + "\n" + fullReport(server, label));
        appendCsv(server, label);
        return file;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        startRecording();
        say(ctx, EventCounter.INSTANCE.available()
                ? "probe: recording. Tick stats reset, counting " + EventCounter.INSTANCE.trackedTypes() + " event types (more are discovered every second)."
                : "probe: recording tick stats only (EventBus internals not reachable, see log).");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        autoStopAtTick = -1;
        Path file = finish(ctx.getSource().getServer(), "manual");
        say(ctx, "probe: stopped, report written to " + file);
        say(ctx, summary());
        return 1;
    }

    /** Pending automatic stop for /probe run (server thread only). */
    private static int autoStopAtTick = -1;
    private static String autoLabel = "";

    private static int run(CommandContext<CommandSourceStack> ctx, int seconds, String label) {
        start(ctx);
        autoStopAtTick = seconds * 20;
        autoLabel = label;
        say(ctx, "probe: run '" + label + "' for " + seconds + " s (" + autoStopAtTick + " ticks)");
        return 1;
    }

    /** From ServerTickEvent.Post (LOWEST). */
    static void onTickEnd(MinecraftServer server) {
        GenBench.onTickEnd(server);
        if (autoStopAtTick >= 0 && TickTimer.INSTANCE.ticks() >= autoStopAtTick) {
            autoStopAtTick = -1;
            Path file = finish(server, autoLabel);
            server.getPlayerList().broadcastSystemMessage(Component.literal("probe: run '" + autoLabel + "' done -> " + file), false);
            ProbeMod.LOGGER.info("probe: run '{}' done -> {}\n{}", autoLabel, file, summary());
        }
    }

    private static Path finish(MinecraftServer server, String label) {
        EventCounter.INSTANCE.stop();
        Path file = write(server, "report-" + label, fullReport(server, label));
        appendCsv(server, label);
        return file;
    }

    private static void appendCsv(MinecraftServer server, String label) {
        TickTimer t = TickTimer.INSTANCE;
        TickTimer.Stats d = t.durationStats();
        TickTimer.Stats a = t.allocStats();
        TickTimer.Stats iv = t.intervalStats();
        long ticks = Math.max(1, t.ticks());
        long events = 0;
        for (EventCounter.Counter c : EventCounter.INSTANCE.snapshotSorted()) {
            events += c.serverThread.sum();
        }
        int entities = 0, tickers = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity ignored : level.getAllEntities()) {
                entities++;
            }
            String bt = blockEntityTickers(level);
            tickers += bt.equals("?") ? 0 : Integer.parseInt(bt);
        }
        Path csv = server.getServerDirectory().resolve("probe").resolve("summary.csv");
        String header = "time,label,ticks,mspt_mean,mspt_p50,mspt_p99,mspt_max,interval_p99,alloc_kib_mean,alloc_kib_p99,events_per_tick,entities,ticking_block_entities,loop_tells_per_tick,mailbox_tasks_per_tick,window_s,"
                + String.join(",", JvmStats.csvHeader()) + "\n";
        String row = String.format(Locale.ROOT, "%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%.1f,%d,%d,%.1f,%.1f,%.1f,%s%n",
                LocalDateTime.now().withNano(0), label, t.ticks(), d.mean() / 1e6, d.p50() / 1e6, d.p99() / 1e6,
                d.max() / 1e6, iv.p99() / 1e6, a.mean() / 1024, a.p99() / 1024.0, (double) events / ticks, entities, tickers,
                (double) TaskStats.totalLoopTells() / ticks, (double) TaskStats.totalMailboxTasks() / ticks,
                t.windowSeconds(), JvmStats.csvRow());
        try {
            Files.createDirectories(csv.getParent());
            if (Files.exists(csv) && !Files.readAllLines(csv).get(0).equals(header.strip())) {
                // schema changed: keep the old file, start a new one
                Files.move(csv, csv.resolveSibling("summary-" + LocalDateTime.now().format(FILE_TS) + "-oldschema.csv"));
            }
            if (!Files.exists(csv)) {
                Files.writeString(csv, header);
            }
            Files.writeString(csv, row, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ProbeMod.LOGGER.error("Cannot append {}", csv, e);
        }
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        say(ctx, summary());
        return 1;
    }

    private static int bench(CommandContext<CommandSourceStack> ctx, int n) {
        if (EventCounter.INSTANCE.enabled()) {
            say(ctx, "probe: run /probe stop first. Counting listeners would distort the bench.");
            return 0;
        }
        say(ctx, "probe: benchmarking IEventBus.post, n=" + n + " (the server thread is blocked while it runs)...");
        long t0 = System.nanoTime();
        List<EventBench.Result> results = EventBench.run(n);
        StringBuilder sb = new StringBuilder();
        sb.append("# IEventBus.post microbenchmark (in-game, server thread)\n")
                .append("n per run = ").append(n).append(", best of 5 after warm-up, took ")
                .append(String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9)).append('\n')
                .append(jvmLine()).append("\n\n");
        for (EventBench.Result r : results) {
            sb.append(r).append('\n');
        }
        Path file = write(ctx.getSource().getServer(), "bench", sb.toString());
        for (EventBench.Result r : results) {
            say(ctx, r.toString());
        }
        say(ctx, "probe: written to " + file);
        return 1;
    }

    private static String summary() {
        TickTimer t = TickTimer.INSTANCE;
        TickTimer.Stats d = t.durationStats();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "ticks=%d  MSPT p50=%.2f p99=%.2f max=%.2f ms  alloc p50=%.0f KiB/tick",
                t.ticks(), d.p50() / 1e6, d.p99() / 1e6, d.max() / 1e6, t.allocStats().p50() / 1024.0));
        List<EventCounter.Counter> top = EventCounter.INSTANCE.snapshotSorted();
        double secs = EventCounter.INSTANCE.windowSeconds();
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            EventCounter.Counter c = top.get(i);
            sb.append(String.format(Locale.ROOT, "%n  %,10.0f/s  %s", c.total() / secs, shortName(c.type)));
        }
        return sb.toString();
    }

    private static String fullReport(MinecraftServer server, String label) {
        TickTimer t = TickTimer.INSTANCE;
        StringBuilder sb = new StringBuilder();
        sb.append("# probe report '").append(label).append("' ").append(LocalDateTime.now()).append('\n')
                .append(jvmLine()).append('\n')
                .append(String.format(Locale.ROOT, "window: %.1f s, %d ticks recorded%n%n", t.windowSeconds(), t.ticks()));

        sb.append("## Server tick (ServerTickEvent.Pre -> Post)\n")
                .append("duration  ").append(t.durationStats().asMillis()).append('\n')
                .append("interval  ").append(t.intervalStats().asMillis()).append("   (target 50 ms; spread = scheduling jitter)\n")
                .append("alloc     ").append(ThreadAlloc.supported() ? t.allocStats().asKiB() : "unsupported").append("   (server thread only)\n\n");

        sb.append("## Per level (LevelTickEvent.Pre -> Post)\n");
        for (Map.Entry<String, TickTimer.LevelStats> e : t.levels().entrySet()) {
            TickTimer.LevelStats s = e.getValue();
            sb.append(String.format(Locale.ROOT, "%-28s mean=%.3f ms max=%.3f ms ticks=%d%n",
                    e.getKey(), s.ticks == 0 ? 0 : s.sum / 1e6 / s.ticks, s.max / 1e6, s.ticks));
        }

        sb.append('\n').append(worldStats(server)).append('\n');
        sb.append(TaskStats.report(Math.max(1e-9, t.windowSeconds()), Math.max(1, t.ticks()))).append('\n');
        sb.append(JvmStats.report()).append('\n');

        List<EventCounter.Counter> counters = EventCounter.INSTANCE.snapshotSorted();
        double secs = EventCounter.INSTANCE.windowSeconds();
        long ticks = Math.max(1, t.ticks());
        sb.append("\n## Events posted on NeoForge.EVENT_BUS (window ").append(String.format(Locale.ROOT, "%.1f s", secs)).append(")\n")
                .append(String.format(Locale.ROOT, "%14s %12s %12s %12s %10s  %s%n", "total", "per sec", "per tick", "off-server", "listeners", "event"));
        for (EventCounter.Counter c : counters) {
            long total = c.total();
            sb.append(String.format(Locale.ROOT, "%,14d %,12.0f %,12.1f %,12d %10s  %s%n",
                    total, total / secs, (double) c.serverThread.sum() / ticks, c.otherThreads.sum(),
                    c.listeners < 0 ? "?" : Integer.toString(c.listeners), c.type.getName()));
        }
        sb.append("\n'off-server' = posted from any thread other than the server thread (render thread, workers, netty).\n")
                .append("'listeners' = listeners one post invokes (incl. parent types), excluding the probe's own counter.\n");
        return sb.toString();
    }

    static String worldStats(MinecraftServer server) {
        StringBuilder sb = new StringBuilder("## World\n");
        for (ServerLevel level : server.getAllLevels()) {
            int entities = 0;
            for (Entity ignored : level.getAllEntities()) {
                entities++;
            }
            sb.append(String.format(Locale.ROOT, "%-28s entities=%d tickingBlockEntities=%s loadedChunks=%d%n",
                    level.dimension().location(), entities, blockEntityTickers(level),
                    level.getChunkSource().getLoadedChunksCount()));
        }
        return sb.toString();
    }

    private static Field tickersField;

    /** Level.blockEntityTickers is protected; runtime names are Mojang names on NeoForge 1.21.1. */
    private static String blockEntityTickers(Level level) {
        try {
            if (tickersField == null) {
                tickersField = Level.class.getDeclaredField("blockEntityTickers");
                tickersField.setAccessible(true);
            }
            return String.valueOf(((List<?>) tickersField.get(level)).size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }

    private static String jvmLine() {
        var rt = ManagementFactory.getRuntimeMXBean();
        return String.format(Locale.ROOT, "JVM %s %s, %d CPUs, max heap %d MiB, args %s",
                rt.getVmVendor(), System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() >> 20, rt.getInputArguments().stream()
                        .filter(a -> a.startsWith("-X") || a.startsWith("-XX")).toList());
    }

    private static String shortName(Class<?> type) {
        String n = type.getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    private static Path write(MinecraftServer server, String kind, String content) {
        Path dir = server.getServerDirectory().resolve("probe");
        Path file = dir.resolve(kind + "-" + LocalDateTime.now().format(FILE_TS) + ".txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, content);
        } catch (IOException e) {
            ProbeMod.LOGGER.error("Cannot write {}", file, e);
        }
        ProbeMod.LOGGER.info("\n{}", content);
        return file.toAbsolutePath();
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String msg) {
        for (String line : msg.split("\n")) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
    }
}
