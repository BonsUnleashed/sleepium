package dev.probe;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Hands-free client test, enabled with {@code -Dprobe.clientAuto=<label>}:
 * <ol>
 *   <li>title screen → create a fresh world "probe-auto" (fixed seed, creative, commands on); render distance 12,
 *       no pause on lost focus</li>
 *   <li>after joining: time until the initial terrain is meshed (section compile queue empty and stable 2 s)</li>
 *   <li>frame recording (ClientProbe, 40 s) with two teleports far away: each measures the time until the new
 *       terrain is generated (server pools) and meshed (client pool) again</li>
 *   <li>thread census, then the client closes itself</li>
 * </ol>
 * Everything is logged with the prefix {@code [probe/auto]}.
 */
final class ClientAuto {
    private static final String LABEL = System.getProperty("probe.clientAuto", "auto");
    private static final String WORLD = "probe-auto";
    private static final long SEED = 20260925L;
    /** -Dprobe.clientAuto.fps=N: frame cap for resource measurements at equal frame rate (default 260 = unlimited) */
    private static final int FPS_CAP = Integer.getInteger("probe.clientAuto.fps", 260);
    /** -Dprobe.clientAuto.steady=true: after the initial terrain, 30 s of frames standing still, no teleports */
    private static final boolean STEADY = Boolean.getBoolean("probe.clientAuto.steady");
    private static final int SETTLE_S = Integer.getInteger("probe.clientAuto.settle", 0);
    /** -Dprobe.clientAuto.scene=client_entities: run that probe function after the terrain is ready, then look at it */
    private static final String SCENE = System.getProperty("probe.clientAuto.scene");
    private static final String SCENE_TP = "tp @s 24 258 4 0 25";
    /** -Dprobe.clientAuto.framehash=true: freeze, then hash the frame's pixels and save it as a PNG (render exactness) */
    private static final boolean FRAMEHASH = Boolean.getBoolean("probe.clientAuto.framehash");
    private static int hashAt = -1, idleTicks, sceneAt;

    /** -Dprobe.clientAuto.jfr=file.jfr[;settings.jfc]: a JFR recording of exactly the measured window */
    private static final String JFR = System.getProperty("probe.clientAuto.jfr");
    private static jdk.jfr.Recording recording;

    private static void startMeasuring() {
        if (DrawStats.ON) DrawStats.start();
        if (JFR != null) {
            try {
                String[] j = JFR.split(";");
                var cfg = j.length > 1 ? jdk.jfr.Configuration.create(Path.of(j[1])) : jdk.jfr.Configuration.getConfiguration("profile");
                recording = new jdk.jfr.Recording(cfg);
                recording.setDestination(Path.of(j[0]));
                recording.start();
            } catch (Exception e) {
                log("jfr failed: " + e);
            }
        }
        if (FRAMEHASH) {
            // no /tick freeze: frozen, the server stops sending the light that new sections need to be built
            dev.probe.optsupport.ClientCounters.freezeTextures = true;
            hashAt = ticks + 60;
        }
        if (STEADY) {
            ClientProbe.start(30, LABEL);   // stationary: rendering only, no loading in the window
            enter(Phase.FINISH);
        } else {
            ClientProbe.start(40, LABEL);
            enter(Phase.RECORD);
        }
    }
    private static Boolean savedVsync;
    private static Integer savedFps, savedDistance;
    private static net.minecraft.client.CloudStatus savedClouds;

    private static void frameHash(Minecraft mc) {
        try (com.mojang.blaze3d.platform.NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            long h = 1469598103934665603L;
            int rows = (int) (img.getHeight() * 0.66);   // the scene; the terrain far below may still be loading
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < img.getWidth(); x++) h = (h ^ (img.getPixelRGBA(x, y) & 0x00FFFFFF)) * 1099511628211L;   // colour only: the framebuffer's alpha is arbitrary
            Path out = mc.gameDirectory.toPath().resolve("probe").resolve("frame-" + LABEL + ".png");
            Files.createDirectories(out.getParent());
            img.writeToFile(out);
            log(String.format(Locale.ROOT, "frame hash %016x (%dx%d) -> %s", h, img.getWidth(), img.getHeight(), out.getFileName()));
        } catch (Exception e) {
            log("frame hash failed: " + e);
        }
    }
    /** -Dprobe.clientAuto.settleExtra=S: keep waiting S more seconds after the terrain is ready (lighting, stragglers) */
    private static final int SETTLE_EXTRA = Integer.getInteger("probe.clientAuto.settleExtra", 0);
    private static long meshedAt;

    private enum Phase { TITLE, JOIN, SETTLE, SCENE_WAIT, RECORD, TP_WAIT_START, TP_WAIT_DONE, FINISH, DONE }

    private static Phase phase = Phase.TITLE;
    private static int ticks, stableTicks, tpIndex;
    private static long phaseStart, joinedAt;
    private static final int[][] TP = {{2000, 2000}, {-2000, -2000}};

    private ClientAuto() {}

    static void register(IEventBus bus) {
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        log("enabled, label '" + LABEL + "'");
    }

    private static void log(String s) {
        ProbeMod.LOGGER.info("[probe/auto] {}", s);
    }

    private static double sinceJvm() {
        return (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000.0;
    }

    private static double since(long t) {
        return (System.nanoTime() - t) / 1e9;
    }

    private static void enter(Phase p) {
        phase = p;
        phaseStart = System.nanoTime();
        stableTicks = 0;
    }

    /** Meshing done: the compile queue is empty and nothing waits for upload, stable for 2 s. */
    private static boolean meshedStable(Minecraft mc) {
        var d = mc.levelRenderer.getSectionRenderDispatcher();
        boolean idle = d != null && mc.levelRenderer.hasRenderedAllSections() && d.getToUpload() == 0 && d.getToBatchCount() == 0;
        stableTicks = idle ? stableTicks + 1 : 0;
        return stableTicks >= 40;
    }

    private static boolean queueBusy(Minecraft mc) {
        var d = mc.levelRenderer.getSectionRenderDispatcher();
        return d != null && (!mc.levelRenderer.hasRenderedAllSections() || d.getToBatchCount() > 0);
    }

    private static void command(Minecraft mc, String cmd) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), cmd);
        });
    }

    private static void tick() {
        if (hashAt >= 0 && ticks >= hashAt) {
            // only when the renderer is idle: every section built and uploaded (the scene's sections may still be queued)
            Minecraft m = Minecraft.getInstance();
            var d = m.levelRenderer.getSectionRenderDispatcher();
            boolean idle = d != null && m.levelRenderer.hasRenderedAllSections() && d.getToUpload() == 0 && d.getToBatchCount() == 0;
            idleTicks = idle ? idleTicks + 1 : 0;
            if (idleTicks >= 20 || ticks >= hashAt + 600) {
                hashAt = -1;
                if (idleTicks < 20) log("frame hash: renderer NOT idle after 30 s");
                frameHash(m);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen) {
                    log(String.format(Locale.ROOT, "title screen at %.1f s since JVM start", sinceJvm()));
                    // remember what we change, restored before the client closes (options.txt is shared with manual play)
                    savedVsync = mc.options.enableVsync().get();
                    savedFps = mc.options.framerateLimit().get();
                    savedClouds = mc.options.cloudStatus().get();
                    savedDistance = mc.options.renderDistance().get();
                    mc.options.renderDistance().set(12);
                    mc.options.pauseOnLostFocus = false;
                    if (FRAMEHASH) {   // nothing that moves by itself
                        mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                        mc.options.hideGui = true;
                    }
                    // measure the renderer, not the monitor: no vsync, no frame cap (260 = unlimited)
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(FPS_CAP);
                    mc.getWindow().updateVsync(false);
                    mc.getWindow().setFramerateLimit(FPS_CAP);
                    Path dir = mc.getLevelSource().getBaseDir().resolve(WORLD);
                    deleteTree(dir);
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT);
                    enter(Phase.JOIN);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
                            WorldPresets::createNormalWorldDimensions, new TitleScreen());
                }
            }
            case JOIN -> {
                if (mc.level != null && mc.player != null && mc.screen == null) {
                    joinedAt = System.nanoTime();
                    log(String.format(Locale.ROOT, "joined: %.1f s after world creation started, %.1f s since JVM start", since(phaseStart), sinceJvm()));
                    if (SCENE != null) command(mc, SCENE_TP);   // go there first: the scene's chunks must be loaded before it is built
                    enter(Phase.SETTLE);
                }
            }
            case SETTLE -> {
                // -Dprobe.clientAuto.settle=S: wait a fixed S seconds instead (renderers that replace vanilla's section
                // dispatcher, like Sodium, do not show their progress in its counters)
                boolean meshed = SETTLE_S > 0 ? since(phaseStart) > SETTLE_S : (meshedStable(mc) || since(phaseStart) > 90);
                if (meshed && meshedAt == 0) meshedAt = System.nanoTime();
                if (meshed && since(meshedAt) >= SETTLE_EXTRA) {
                    log(String.format(Locale.ROOT, "initial terrain meshed in %.1f s (%s)%s", since(phaseStart) - 2.0,
                            mc.levelRenderer.getSectionStatistics(), since(phaseStart) > 90 ? " TIMEOUT" : ""));
                    command(mc, "gamemode spectator");
                    if (SCENE != null) {   // a rendering scene in the sky: build it now (chunks loaded), then wait for its meshes
                        command(mc, SCENE_TP);   // again: the join may still move the player after the first one
                        command(mc, "function probe:" + SCENE);
                        sceneAt = ticks;
                        idleTicks = 0;
                        enter(Phase.SCENE_WAIT);
                    } else {
                        startMeasuring();
                    }
                }
            }
            case SCENE_WAIT -> {
                // every section built and uploaded for 5 s; at most 60 s
                var d = mc.levelRenderer.getSectionRenderDispatcher();
                boolean idle = ticks > sceneAt + 20 && d != null && mc.levelRenderer.hasRenderedAllSections() && d.getToUpload() == 0 && d.getToBatchCount() == 0;
                idleTicks = idle ? idleTicks + 1 : 0;
                if (idleTicks >= 100 || ticks > sceneAt + 1200) {
                    log(String.format(Locale.ROOT, "scene built and meshed after %.1f s%s", (ticks - sceneAt) / 20.0, idleTicks >= 100 ? "" : " TIMEOUT"));
                    idleTicks = 0;
                    startMeasuring();
                }
            }
            case RECORD -> {
                if (since(phaseStart) > 3) {
                    int[] p = TP[tpIndex];
                    command(mc, "tp @s " + p[0] + " 140 " + p[1]);
                    enter(Phase.TP_WAIT_START);
                }
            }
            case TP_WAIT_START -> {
                if (queueBusy(mc) || since(phaseStart) > 5) enter2Done();
            }
            case TP_WAIT_DONE -> {
                if (meshedStable(mc) || since(phaseStart) > 60) {
                    int[] p = TP[tpIndex];
                    log(String.format(Locale.ROOT, "teleport %d to (%d, %d): terrain generated + meshed in %.1f s (%s)%s", tpIndex + 1, p[0], p[1],
                            since(tpStart) - 2.0, mc.levelRenderer.getSectionStatistics(), since(phaseStart) > 60 ? " TIMEOUT" : ""));
                    tpIndex++;
                    if (tpIndex < TP.length) enter(Phase.RECORD);
                    else enter(Phase.FINISH);
                }
            }
            case FINISH -> {
                if (!ClientProbe.running()) {
                    if (recording != null) {
                        recording.stop();   // written to its destination
                        recording.close();
                        recording = null;
                    }
                    log(dev.probe.optsupport.ClientCounters.report());
                    if (DrawStats.ON) log(DrawStats.report());
                    log("thread census:\n" + census());
                    enter(Phase.DONE);
                    if (savedFps != null) {
                        mc.options.enableVsync().set(savedVsync);
                        mc.options.framerateLimit().set(savedFps);
                        mc.options.cloudStatus().set(savedClouds);
                        mc.options.renderDistance().set(savedDistance);
                        mc.options.hideGui = false;
                        mc.options.save();
                    }
                    mc.stop();
                }
            }
            case DONE -> {}
        }
    }

    private static long tpStart;

    private static void enter2Done() {
        tpStart = phaseStart;   // measured from the teleport command
        phase = Phase.TP_WAIT_DONE;
        stableTicks = 0;
    }

    static String census() {
        var mx = ManagementFactory.getThreadMXBean();
        TreeMap<String, int[]> g = new TreeMap<>();
        Pattern p = Pattern.compile("^(.*?)[-# ]?(\\d+)$");
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            Matcher m = p.matcher(t.getName());
            String key = m.matches() ? m.group(1) : t.getName();
            int idx = m.matches() ? Integer.parseInt(m.group(2)) : 0;
            int[] v = g.computeIfAbsent(key, k -> new int[2]);
            v[0]++;
            v[1] = Math.max(v[1], idx);
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "threads: live %d, peak %d, total started since JVM start %d%n",
                mx.getThreadCount(), mx.getPeakThreadCount(), mx.getTotalStartedThreadCount()));
        g.forEach((k, v) -> sb.append(String.format(Locale.ROOT, "  %-44s live %3d   highest index %4d%n", k, v[0], v[1])));
        return sb.toString();
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(q -> q.toFile().delete());
        } catch (IOException e) {
            log("cannot delete " + dir + ": " + e);
        }
    }
}
