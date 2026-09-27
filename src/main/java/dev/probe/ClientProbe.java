package dev.probe;

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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Client-side frame probe. Loaded only on the physical client (see ProbeMod).
 *
 * <p>{@code /probeclient run <seconds> <label>} records, per frame, on the render thread:
 * <ul>
 *   <li>frame   – RenderFrameEvent.Pre -> next Pre (full frame incl. client tick, swap, vsync wait)</li>
 *   <li>render  – Pre -> Post (GameRenderer.render: CPU-side world + GUI submission)</li>
 *   <li>alloc   – bytes allocated by the render thread per frame</li>
 * </ul>
 * and appends a row to {@code <gamedir>/probe/summary-client.csv}. Cross-thread task counters (render thread
 * event loop, "Section Renderer" mailbox = chunk mesh rebuilds) come from TaskStats.
 */
final class ClientProbe {
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

    private ClientProbe() {}

    static void register(IEventBus bus) {
        if (System.getProperty("probe.clientAuto") != null) ClientAuto.register(bus);
        bus.addListener(EventPriority.HIGHEST, RenderFrameEvent.Pre.class, e -> onPre());
        bus.addListener(EventPriority.LOWEST, RenderFrameEvent.Post.class, e -> onPost());
        bus.addListener(EventPriority.HIGHEST, net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, e -> tickStart = System.nanoTime());
        bus.addListener(EventPriority.LOWEST, net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
            if (stopAtNanos > 0 && tickStart != 0 && nt < ticks.length) ticks[nt++] = System.nanoTime() - tickStart;
        });
        bus.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal("probeclient").then(Commands.literal("run")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
                                .then(Commands.argument("label", StringArgumentType.word()).executes(ctx -> {
                                    start(IntegerArgumentType.getInteger(ctx, "seconds"), StringArgumentType.getString(ctx, "label"));
                                    ctx.getSource().sendSuccess(() -> Component.literal("probeclient: recording '" + label + "'"), false);
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
        stopAtNanos = startNanos + seconds * 1_000_000_000L;
    }

    private static long cpuAtStart;

    /** CPU time of the whole process (render thread, driver threads, workers, the integrated server), ns; -1 if unknown */
    private static long processCpu() {
        return java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getProcessCpuTime() : -1;
    }

    private static void onPre() {
        dev.probe.optsupport.OwnerThread.client = Thread.currentThread();
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
        String sections = mc.levelRenderer.getSectionStatistics();
        String entities = mc.levelRenderer.getEntityStatistics();

        StringBuilder sb = new StringBuilder();
        sb.append("# probe client report '").append(label).append("' ").append(LocalDateTime.now()).append('\n')
                .append(String.format(Locale.ROOT, "window %.1f s, %d frames, %.1f fps avg%n", secs, n, n / secs))
                .append(String.format(Locale.ROOT, "process CPU %.1f s (%.2f cores), %.3f ms per frame%n", cpuSecs, cpuSecs / secs, cpuSecs * 1e3 / Math.max(1, n)))
                .append(mc.getLaunchedVersion()).append(", ").append(com.mojang.blaze3d.platform.GlUtil.getRenderer()).append("\n\n")
                .append("frame   ").append(f.asMillis()).append('\n')
                .append("render  ").append(r.asMillis()).append("   (GameRenderer.render, CPU side)\n")
                .append("alloc   ").append(a.asKiB().replace("/tick", "/frame")).append("   (render thread)\n")
                .append("tick    ").append(t.asMillis()).append("   (Minecraft.tick, 20 Hz)\n")
                .append(String.format(Locale.ROOT, "stutters (frame > 2x median): %d (%.2f %%)%n", stutters, 100.0 * stutters / Math.max(1, n)))
                .append(sections).append('\n').append(entities).append("\n\n")
                .append(TaskStats.report(secs, Math.max(1, n)).replace("per tick", "per frm "));

        Path dir = mc.gameDirectory.toPath().resolve("probe");
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
            ProbeMod.LOGGER.error("Cannot write {}", file, e);
        }
        ProbeMod.LOGGER.info("\n{}", sb);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "probeclient '%s': %.0f fps, frame p50 %.2f / p99 %.2f ms, %.0f KiB/frame, CPU %.2f cores / %.3f ms per frame, client tick mean %.3f / p99 %.3f ms -> %s",
                    label, n / secs, f.p50() / 1e6, f.p99() / 1e6, a.mean() / 1024, cpuSecs / secs, cpuSecs * 1e3 / Math.max(1, n), t.mean() / 1e6, t.p99() / 1e6, file.getFileName())), false);
        }
    }
}
