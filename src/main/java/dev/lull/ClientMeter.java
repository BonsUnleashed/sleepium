package dev.lull;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side frame probe. Loaded only on the physical client (see SleepMod).
 *
 * <p>{@code /sleepclient run <seconds> <label>} records, per frame, on the render thread:
 * <ul>
 *   <li>frame   – RenderFrameEvent.Pre -> next Pre (full frame incl. client tick, swap, vsync wait)</li>
 *   <li>render  – Pre -> Post (GameRenderer.render: CPU-side world + GUI submission)</li>
 *   <li>alloc   – bytes allocated by the render thread per frame</li>
 * </ul>
 * and appends a row to {@code <gamedir>/probe/summary-client.csv}. Cross-thread task counters (render thread
 * event loop, "Section Renderer" mailbox = chunk mesh rebuilds) come from TaskStats.
 */
final class ClientMeter {
    private static final int CAPACITY = 1 << 17;   // ~36 min at 60 fps
    private static final long[] frames = new long[CAPACITY];
    private static final long[] renders = new long[CAPACITY];
    private static final long[] allocs = new long[CAPACITY];
    private static final long[] ticks = new long[CAPACITY / 4];   // Minecraft.tick durations (ClientTickEvent Pre -> Post)
    private static int nt;
    private static long tickStart;
    private static int n;
    private static long lastPre, preNanos, allocAtPre;
    private static long stopAtNanos = -1, startNanos;
    private static String label = "";

    private ClientMeter() {}

    static void register(IEventBus bus) {
        if (System.getProperty("sleep.clientAuto") != null) ClientAuto.register(bus);
        // Forge 1.20.1: RenderTickEvent START / END is 1.21's RenderFrameEvent Pre / Post (both around GameRenderer.render
        // in Minecraft.runTick), ClientTickEvent START / END is ClientTickEvent Pre / Post
        bus.addListener(EventPriority.HIGHEST, false, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) onPre();
        });
        bus.addListener(EventPriority.LOWEST, false, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END) onPost();
        });
        bus.addListener(EventPriority.HIGHEST, false, TickEvent.ClientTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) tickStart = System.nanoTime();
        });
        bus.addListener(EventPriority.LOWEST, false, TickEvent.ClientTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END && stopAtNanos > 0 && tickStart != 0 && nt < ticks.length) ticks[nt++] = System.nanoTime() - tickStart;
        });
        bus.addListener(EventPriority.NORMAL, false, RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(SleepMod.DEV ? "sleepclient" : "sleepiumclient").then(Commands.literal("run")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
                                .then(Commands.argument("label", StringArgumentType.word()).executes(ctx -> {
                                    start(IntegerArgumentType.getInteger(ctx, "seconds"), StringArgumentType.getString(ctx, "label"));
                                    ctx.getSource().sendSuccess(() -> Component.literal("sleepclient: recording '" + label + "'"), false);
                                    return 1;
                                }))))));
    }

    static boolean running() {
        return stopAtNanos >= 0;
    }

    static void start(int seconds, String l) {
        nt = 0;
        n = 0;
        lastPre = 0;
        label = l;
        TaskStats.reset();
        startNanos = System.nanoTime();
        cpuAtStart = processCpu();
        threadsAtStart = threadCpu();
        stopAtNanos = startNanos + seconds * 1_000_000_000L;
    }

    private static long cpuAtStart;
    private static java.util.Map<Long, long[]> threadsAtStart = java.util.Map.of();
    private static final java.util.Map<Long, String> NAMES = new java.util.HashMap<>();

    /** CPU time per live thread (ns), names remembered */
    private static java.util.Map<Long, long[]> threadCpu() {
        var mx = java.lang.management.ManagementFactory.getThreadMXBean();
        java.util.Map<Long, long[]> m = new java.util.HashMap<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            long c = mx.getThreadCpuTime(t.getId());
            if (c >= 0) {
                m.put(t.getId(), new long[] {c});
                NAMES.put(t.getId(), t.getName().replaceAll("\\d+", "N"));
            }
        }
        return m;
    }

    /** Java threads' CPU in the window by group, busiest first (JVM-internal threads: the rest of process CPU) */
    private static String threadReport(double secs) {
        java.util.Map<Long, long[]> now = threadCpu();
        java.util.Map<String, Long> by = new java.util.TreeMap<>();
        for (var e : now.entrySet()) {
            long[] a = threadsAtStart.get(e.getKey());
            long d = e.getValue()[0] - (a == null ? 0 : a[0]);
            by.merge(NAMES.getOrDefault(e.getKey(), "?"), d, Long::sum);
        }
        StringBuilder b = new StringBuilder("java threads (cores):");
        by.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(10)
                .forEach(e -> b.append(String.format(Locale.ROOT, " %s %.2f;", e.getKey(), e.getValue() / 1e9 / secs)));
        return b.toString();
    }

    /** CPU time of the whole process (render thread, driver threads, workers, the integrated server), ns; -1 if unknown */
    private static long processCpu() {
        return java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getProcessCpuTime() : -1;
    }

    private static void onPre() {
        dev.lull.optsupport.OwnerThread.client = Thread.currentThread();
        long now = System.nanoTime();
        if (stopAtNanos < 0) {
            return;
        }
        if (lastPre != 0 && n < CAPACITY) {
            frames[n] = now - lastPre;
            allocs[n] = ThreadAlloc.supported() ? ThreadAlloc.current() - allocAtPre : 0;
            n++;
        }
        lastPre = now;
        preNanos = now;
        allocAtPre = ThreadAlloc.current();
        if (now >= stopAtNanos) {
            stopAtNanos = -1;
            finish();
        }
    }

    private static void onPost() {
        if (stopAtNanos >= 0 && n < CAPACITY) {
            renders[n] = System.nanoTime() - preNanos;   // belongs to the frame that is completed at the next Pre
        }
        // -Dlab.rcache.spikes (the render cache's slow-frame report): the render cache is not on 1.20.1 yet (stage 3)
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        double secs = (System.nanoTime() - startNanos) / 1e9;
        long cpuNow = processCpu();
        double cpuSecs = cpuAtStart >= 0 && cpuNow >= 0 ? (cpuNow - cpuAtStart) / 1e9 : Double.NaN;
        TickTimer.Stats f = TickTimer.Stats.of(frames, n);
        TickTimer.Stats r = TickTimer.Stats.of(renders, n);
        TickTimer.Stats a = TickTimer.Stats.of(allocs, n);
        TickTimer.Stats t = TickTimer.Stats.of(ticks, nt);
        int stutters = 0;
        for (int i = 0; i < n; i++) {
            if (frames[i] > 2 * f.p50()) {
                stutters++;
            }
        }
        String sections = mc.levelRenderer.getChunkStatistics();
        String entities = mc.levelRenderer.getEntityStatistics();

        StringBuilder sb = new StringBuilder();
        sb.append("# sleep client report '").append(label).append("' ").append(LocalDateTime.now()).append('\n')
                .append(String.format(Locale.ROOT, "window %.1f s, %d frames, %.1f fps avg%n", secs, n, n / secs))
                .append(String.format(Locale.ROOT, "process CPU %.1f s (%.2f cores), %.3f ms per frame%n", cpuSecs, cpuSecs / secs, cpuSecs * 1e3 / Math.max(1, n)))
                .append(threadReport(secs)).append('\n')
                .append(mc.getLaunchedVersion()).append(", ").append(com.mojang.blaze3d.platform.GlUtil.getRenderer()).append("\n\n")
                .append("frame   ").append(f.asMillis()).append('\n')
                .append("render  ").append(r.asMillis()).append("   (GameRenderer.render, CPU side)\n")
                .append("alloc   ").append(a.asKiB().replace("/tick", "/frame")).append("   (render thread)\n")
                .append("tick    ").append(t.asMillis()).append("   (Minecraft.tick, 20 Hz)\n")
                .append(String.format(Locale.ROOT, "stutters (frame > 2x median): %d (%.2f %%)%n", stutters, 100.0 * stutters / Math.max(1, n)))
                .append(sections).append('\n').append(entities).append("\n\n")
                .append(TaskStats.report(secs, Math.max(1, n)).replace("per tick", "per frm "));

        Path dir = mc.gameDirectory.toPath().resolve(dev.lull.SleepOut.ROOT);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("report-client-" + label + "-" + ts + ".txt");
        Path csv = dir.resolve("summary-client.csv");
        String row = String.format(Locale.ROOT, "%s,%s,%d,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f,%.1f,%.1f,%d%n",
                LocalDateTime.now().withNano(0), label, n, n / secs, f.p50() / 1e6, f.p99() / 1e6, f.max() / 1e6,
                r.p50() / 1e6, r.p99() / 1e6, a.mean() / 1024, a.p99() / 1024.0, stutters);
        try {
            Files.createDirectories(dir);
            Files.writeString(file, sb.toString());
            if (!Files.exists(csv)) {
                Files.writeString(csv, "time,label,frames,fps,frame_p50,frame_p99,frame_max,render_p50,render_p99,alloc_kib_mean,alloc_kib_p99,stutters\n");
            }
            Files.writeString(csv, row, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SleepMod.LOGGER.error("Cannot write {}", file, e);
        }
        SleepMod.LOGGER.info("\n{}", sb);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "sleepclient '%s': %.0f fps, frame p50 %.2f / p99 %.2f ms, %.0f KiB/frame, CPU %.2f cores / %.3f ms per frame, client tick mean %.3f / p99 %.3f ms -> %s",
                    label, n / secs, f.p50() / 1e6, f.p99() / 1e6, a.mean() / 1024, cpuSecs / secs, cpuSecs * 1e3 / Math.max(1, n), t.mean() / 1e6, t.p99() / 1e6, file.getFileName())), false);
        }
    }
}
