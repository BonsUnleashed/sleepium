package dev.sleep;

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
 * Hands-free client test, enabled with {@code -Dsleep.clientAuto=<label>}:
 * <ol>
 *   <li>title screen → create a fresh world "sleep-auto" (fixed seed, creative, commands on); render distance 12,
 *       no pause on lost focus</li>
 *   <li>after joining: time until the initial terrain is meshed (section compile queue empty and stable 2 s)</li>
 *   <li>frame recording (ClientMeter, 40 s) with two teleports far away: each measures the time until the new
 *       terrain is generated (server pools) and meshed (client pool) again</li>
 *   <li>thread census, then the client closes itself</li>
 * </ol>
 * Everything is logged with the prefix {@code [sleep/auto]}.
 */
public final class ClientAuto {
    /** other mods' reports at the end of a run, given its label (null: nothing); log with their own files if they need */
    public static final java.util.List<java.util.function.Function<String, String>> REPORTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    /**
     * The function a scene name means: "ns:name" as given; else the engine's (sleep:) when the integrated server has
     * it, then any other namespace's function of that name (a mod's own scenes), else sleep:.
     */
    private static String sceneFunction(Minecraft mc, String scene) {
        if (scene.contains(":")) return scene;
        var server = mc.getSingleplayerServer();
        if (server != null) {
            var functions = server.getFunctions();
            if (functions.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sleep", scene)).isPresent()) return "sleep:" + scene;
            for (net.minecraft.resources.ResourceLocation id : functions.getFunctionNames())
                if (id.getPath().equals(scene)) return id.toString();
        }
        return "sleep:" + scene;
    }

    /** other mods' work once the client is in the world (before the scripted steps) */
    public static final java.util.List<Runnable> READY = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final String LABEL = System.getProperty("sleep.clientAuto", "auto");
    private static final String WORLD = "sleep-auto";
    private static final long SEED = 20260925L;
    /** -Dsleep.clientAuto.fps=N: frame cap for resource measurements at equal frame rate (default 260 = unlimited) */
    private static final int FPS_CAP = Integer.getInteger("sleep.clientAuto.fps", 260);
    /** -Dsleep.clientAuto.steady=true: after the initial terrain, 30 s (steadySeconds) of frames standing still, no teleports */
    private static final boolean STEADY = Boolean.getBoolean("sleep.clientAuto.steady");
    private static final int SETTLE_S = Integer.getInteger("sleep.clientAuto.settle", 0);
    /** -Dsleep.clientAuto.scene=client_entities: run that probe function after the terrain is ready, then look at it */
    private static final String SCENE = System.getProperty("sleep.clientAuto.scene");
    /** -Dsleep.clientAuto.server=host:port: join that server instead of creating a world (commands go as chat commands; op the player) */
    private static final String SERVER = System.getProperty("sleep.clientAuto.server");
    /** -Dsleep.clientAuto.tp="x y z yaw pitch": where the scene is looked at from (default: the sky platform's view) */
    private static final String SCENE_TP = "tp @s " + System.getProperty("sleep.clientAuto.tp", "24 258 4 0 25").replace(',', ' ');   // commas allowed (JVM flags split on spaces)
    /** -Dsleep.clientAuto.sceneWait=S: the scene is measured no earlier than S seconds after it was built (scenes that change for a while) */
    private static final int SCENE_MIN_S = Integer.getInteger("sleep.clientAuto.sceneWait", 0);
    /** -Dsleep.clientAuto.framehash=true: freeze, then hash the frame's pixels and save it as a PNG (render exactness) */
    private static final boolean FRAMEHASH = Boolean.getBoolean("sleep.clientAuto.framehash");
    private static int hashAt = -1, idleTicks, sceneAt;
    /** -Dsleep.clientAuto.shot=S: a screenshot S seconds into the measured window, nothing frozen (moving scenes) */
    private static final double SHOT_S = Double.parseDouble(System.getProperty("sleep.clientAuto.shot", "-1"));   // seconds, fractions allowed
    private static int shotAt = -1;
    /** -Dsleep.clientAuto.gamemode=survival: the game mode once the terrain is ready (default spectator) */
    private static final String GAMEMODE = System.getProperty("sleep.clientAuto.gamemode", "spectator");
    /** -Dsleep.clientAuto.cmdAt=S:command[|S:command...]: server commands S seconds into the measured window (e.g. 10:reload) */
    private static final String CMD_AT = System.getProperty("sleep.clientAuto.cmdAt");
    private static final java.util.List<Object[]> CMDS = new java.util.ArrayList<>();

    /** -Dsleep.clientAuto.jfr=file.jfr[;settings.jfc]: a JFR recording of exactly the measured window */
    private static final String JFR = System.getProperty("sleep.clientAuto.jfr");
    private static jdk.jfr.Recording recording;

    /** -Dsleep.clientAuto.serverProbe=true: the integrated server's tick stats over the same window (probe run) */
    private static final boolean SERVER_PROBE = Boolean.getBoolean("sleep.clientAuto.serverProbe");

    /**
     * Frame hashes: every animated texture (water, lava, fire, portals...) put on its first frame before the freeze —
     * a plain stop leaves each on whatever frame the run's tick count reached, different from run to run.
     */
    private static void resetAnimations() {
        try {
            var tm = Minecraft.getInstance().getTextureManager();
            java.lang.reflect.Field byPath = net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredField("byPath");
            byPath.setAccessible(true);
            java.lang.reflect.Field animated = net.minecraft.client.renderer.texture.TextureAtlas.class.getDeclaredField("animatedTextures");
            animated.setAccessible(true);
            int n = 0;
            for (Object t : ((java.util.Map<?, ?>) byPath.get(tm)).values()) {
                if (!(t instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) continue;
                atlas.bind();
                for (Object tk : (java.util.List<?>) animated.get(atlas)) {
                    java.lang.reflect.Field st = tk.getClass().getDeclaredField("val$spriteticker"), owner = tk.getClass().getDeclaredField("this$0");
                    st.setAccessible(true);
                    owner.setAccessible(true);
                    Object ticker = st.get(tk);
                    var sprite = (net.minecraft.client.renderer.texture.TextureAtlasSprite) owner.get(tk);
                    Class<?> tc = ticker.getClass();
                    java.lang.reflect.Field frame = tc.getDeclaredField("frame"), sub = tc.getDeclaredField("subFrame"), info = tc.getDeclaredField("animationInfo");
                    frame.setAccessible(true);
                    sub.setAccessible(true);
                    info.setAccessible(true);
                    Object anim = info.get(ticker);
                    java.lang.reflect.Field frames = anim.getClass().getDeclaredField("frames");
                    frames.setAccessible(true);
                    Object first = ((java.util.List<?>) frames.get(anim)).get(0);
                    java.lang.reflect.Field index = first.getClass().getDeclaredField("index");
                    index.setAccessible(true);
                    java.lang.reflect.Method upload = anim.getClass().getDeclaredMethod("uploadFrame", int.class, int.class, int.class);
                    upload.setAccessible(true);
                    frame.setInt(ticker, 0);
                    sub.setInt(ticker, 0);
                    upload.invoke(anim, sprite.getX(), sprite.getY(), index.getInt(first));
                    n++;
                }
            }
            log("frame hash: " + n + " animated textures reset to their first frame");
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("frame hash: animation reset failed: " + e);
        }
    }

    private static void startMeasuring() {
        log("measuring started");   // the marker external profilers wait for (uprof_run.sh)
        if (DrawStats.ON) DrawStats.start();
        if (SERVER_PROBE) command(Minecraft.getInstance(), "sleep run 30 " + LABEL + "-srv");
        if (SERVER != null && !"false".equals(System.getProperty("sleep.clientAuto.netBench"))) command(Minecraft.getInstance(), "sleep net " + (STEADY ? Integer.getInteger("sleep.clientAuto.steadySeconds", 30) : 40) + " " + LABEL);
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
            dev.sleep.optsupport.ClientCounters.freezeTextures = true;
            resetAnimations();
            hashAt = ticks + 60;
            // -Dsleep.clientAuto.remesh=true: the scene's sections re-meshed first (the plain re-mesh path, light as it is
            // now): every renderer takes the light at the same moment, whatever each kept since the last light change
            if (Boolean.getBoolean("sleep.clientAuto.remesh")) Minecraft.getInstance().levelRenderer.setBlocksDirty(-16, 216, 0, 64, 272, 80);
        }
        windowStart = ticks;
        if (CMD_AT != null) for (String c : CMD_AT.split("[|]")) {
            int i = c.indexOf(':');
            // spaces are spelled '_' (JVM flags split on spaces), an underscore itself '__' (ids such as mymod:long_name)
            String cmd = c.substring(i + 1).replace("__", "\0").replace('_', ' ').replace('\0', '_');
            CMDS.add(new Object[] {ticks + Integer.parseInt(c.substring(0, i).trim()) * 20, cmd});
        }
        if (Boolean.getBoolean("sleep.clientAuto.thirdPerson")) Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        if (!SHOTS.isEmpty()) {
            shotsBase = ticks;
            Minecraft.getInstance().options.hideGui = !Boolean.getBoolean("sleep.clientAuto.shotGui");
        }
        if (SHOT_S >= 0) {
            shotAt = ticks + (int) Math.round(SHOT_S * 20);
            Minecraft.getInstance().options.hideGui = !Boolean.getBoolean("sleep.clientAuto.shotGui");   // shotGui: the HUD in the shot
        }
        if (STEADY) {
            if (dev.sleep.optsupport.LightTiming.ON) dev.sleep.optsupport.LightTiming.reset();
            ClientMeter.start(Integer.getInteger("sleep.clientAuto.steadySeconds", 30), LABEL);   // stationary: rendering only, no loading in the window
            enter(Phase.FINISH);
        } else {
            ClientMeter.start(40, LABEL);
            enter(Phase.RECORD);
        }
    }
    private static Boolean savedVsync;
    private static Integer savedFps, savedDistance;
    private static net.minecraft.client.CloudStatus savedClouds;

    /**
     * -Dsleep.clientAuto.shots="S:x,y,z,yaw,pitch;S:...": several views in one run - at S seconds into the measured
     * window the camera is moved there, and half a second later frame-<label>-<i>.png is taken; "S:-" takes it at S
     * without moving
     */
    private static final java.util.List<double[]> SHOTS = new java.util.ArrayList<>();
    static {
        String sp = System.getProperty("sleep.clientAuto.shots");
        if (sp != null) for (String one : sp.split(";")) {
            String[] a = one.split(":");
            if (a[1].trim().equals("-")) {   // "S:-": a shot exactly at S, where the camera is (no teleport, no half-second wait)
                SHOTS.add(new double[] {Double.parseDouble(a[0]), Double.NaN, 0, 0, 0, 0, -1});
                continue;
            }
            String[] v = a[1].split(",");
            SHOTS.add(new double[] {Double.parseDouble(a[0]), Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]),
                    Double.parseDouble(v[3]), Double.parseDouble(v[4]), -1});
        }
    }
    private static int shotsBase = -1;
    /** told of each shot taken: its index and its time in the run's seconds (a mod's trace can mark it) */
    public static volatile java.util.function.BiConsumer<Integer, Double> onShot;

    private static void shotsTick(Minecraft mc) {
        if (SHOTS.isEmpty() || shotsBase < 0) return;
        for (int i = 0; i < SHOTS.size(); i++) {
            double[] s = SHOTS.get(i);   // s[6]: -1 pending, > 0 the tick it is taken, 0 taken
            if (s[6] < 0 && ticks >= shotsBase + (int) Math.round(s[0] * 20)) {
                if (Double.isNaN(s[1])) {
                    s[6] = ticks;   // "S:-": this tick, where the camera is
                } else {
                    command(mc, String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", s[1], s[2], s[3], s[4], s[5]));
                    s[6] = ticks + 10;   // the teleported view settles half a second
                }
            }
            if (s[6] > 0 && ticks >= s[6]) {
                s[6] = 0;
                try (com.mojang.blaze3d.platform.NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    Path out = SleepOut.dir(mc.gameDirectory.toPath(), "frame", LABEL).resolve("frame-" + LABEL + "-" + i + ".png");
                    Files.createDirectories(out.getParent());
                    img.writeToFile(out);
                    log("shot " + i + " -> " + out.getFileName());
                    var hook = onShot;
                    if (hook != null) hook.accept(i, s[0]);
                } catch (Exception e) {
                    log("shot " + i + " failed: " + e);
                }
            }
        }
    }

    private static void frameHash(Minecraft mc) {
        try (com.mojang.blaze3d.platform.NativeImage img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            long h = 1469598103934665603L;
            int rows = (int) (img.getHeight() * 0.66);   // the scene; the terrain far below may still be loading
            for (int y = 0; y < rows; y++)
                for (int x = 0; x < img.getWidth(); x++) h = (h ^ (img.getPixelRGBA(x, y) & 0x00FFFFFF)) * 1099511628211L;   // colour only: the framebuffer's alpha is arbitrary
            Path out = SleepOut.dir(mc.gameDirectory.toPath(), "frame", LABEL).resolve("frame-" + LABEL + ".png");
            Files.createDirectories(out.getParent());
            img.writeToFile(out);
            log(String.format(Locale.ROOT, "frame hash %016x (%dx%d) -> %s", h, img.getWidth(), img.getHeight(), out.getFileName()));
        } catch (Exception e) {
            log("frame hash failed: " + e);
        }
    }
    /** -Dsleep.clientAuto.settleExtra=S: keep waiting S more seconds after the terrain is ready (lighting, stragglers) */
    private static final int SETTLE_EXTRA = Integer.getInteger("sleep.clientAuto.settleExtra", 0);
    /** -Dsleep.clientAuto.stopInTp=S: quit the game S seconds after the last teleport's command (a shutdown during generation) */
    private static final int STOP_IN_TP = Integer.getInteger("sleep.clientAuto.stopInTp", -1);
    private static long meshedAt;

    private enum Phase { TITLE, JOIN, SETTLE, SCENE_WAIT, RECORD, TP_WAIT_START, TP_WAIT_DONE, FINISH, DONE }

    private static Phase phase = Phase.TITLE;
    private static int ticks, stableTicks, tpIndex;
    private static int windowStart = -1;

    /** client ticks since the measured window began (test content times its own inputs by it), or -1 before */
    public static int windowTicks() {
        return windowStart < 0 ? -1 : ticks - windowStart;
    }
    private static long phaseStart, joinedAt;
    private static final int[][] TP = {{2000, 2000}, {-2000, -2000}};

    private ClientAuto() {}

    static void register(IEventBus bus) {
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        log("enabled, label '" + LABEL + "'");
    }

    private static void log(String s) {
        SleepMod.LOGGER.info("[sleep/auto] {}", s);
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
        if (dev.sleep.optsupport.GenTrace.ON && d != null) {   // what keeps the view from being stable
            dev.sleep.optsupport.GenTrace.clientTicks.increment();
            if (!mc.levelRenderer.hasRenderedAllSections()) dev.sleep.optsupport.GenTrace.notAllRendered.increment();
            if (d.getToBatchCount() > 0) dev.sleep.optsupport.GenTrace.batching.increment();
            if (d.getToUpload() > 0) dev.sleep.optsupport.GenTrace.uploading.increment();
            dev.sleep.optsupport.GenTrace.toBatchMax = Math.max(dev.sleep.optsupport.GenTrace.toBatchMax, d.getToBatchCount());
        }
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
        if (server == null && mc.player != null && mc.getConnection() != null) {   // a remote server: a chat command
            mc.getConnection().sendCommand(cmd);
            return;
        }
        if (server == null || mc.player == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), cmd);
        });
    }

    private static void tick() {
        for (java.util.Iterator<Object[]> it = CMDS.iterator(); it.hasNext(); ) {
            Object[] c = it.next();
            if (ticks >= (int) c[0]) {
                it.remove();
                log("command at " + ticks + ": /" + c[1]);
                command(Minecraft.getInstance(), (String) c[1]);
            }
        }
        if (shotAt >= 0 && ticks >= shotAt) {
            shotAt = -1;
            frameHash(Minecraft.getInstance());
        }
        shotsTick(Minecraft.getInstance());
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
                    String gs = System.getProperty("sleep.clientAuto.guiScale");   // the interface's scale in the shots (0: auto)
                    if (gs != null) {
                        mc.options.guiScale().set(Integer.parseInt(gs));
                        mc.resizeDisplay();
                    }
                    if (SERVER != null) {
                        enter(Phase.JOIN);
                        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(SERVER),
                                new net.minecraft.client.multiplayer.ServerData("sleep", SERVER, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                        return;
                    }
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
                    for (Runnable r : READY) r.run();   // other mods' work once the world is up (the framework's bake)
                    joinedAt = System.nanoTime();
                    log(String.format(Locale.ROOT, "joined: %.1f s after world creation started, %.1f s since JVM start", since(phaseStart), sinceJvm()));
                    if (SCENE != null) command(mc, SCENE_TP);   // go there first: the scene's chunks must be loaded before it is built
                    enter(Phase.SETTLE);
                }
            }
            case SETTLE -> {
                // -Dsleep.clientAuto.settle=S: wait a fixed S seconds instead (renderers that replace vanilla's section
                // dispatcher, like Sodium, do not show their progress in its counters)
                boolean meshed = SETTLE_S > 0 ? since(phaseStart) > SETTLE_S : (meshedStable(mc) || since(phaseStart) > 90);
                if (meshed && meshedAt == 0) meshedAt = System.nanoTime();
                if (meshed && since(meshedAt) >= SETTLE_EXTRA) {
                    log(String.format(Locale.ROOT, "initial terrain meshed in %.1f s (%s)%s", since(phaseStart) - 2.0,
                            mc.levelRenderer.getSectionStatistics(), since(phaseStart) > 90 ? " TIMEOUT" : ""));
                    command(mc, "gamemode " + GAMEMODE);
                    if (SCENE != null) {   // a rendering scene in the sky: build it now (chunks loaded), then wait for its meshes
                        command(mc, SCENE_TP);   // again: the join may still move the player after the first one
                        command(mc, "function " + sceneFunction(mc, SCENE));
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
                if ((idleTicks >= 100 && ticks >= sceneAt + SCENE_MIN_S * 20) || ticks > sceneAt + 1200) {
                    log(String.format(Locale.ROOT, "scene built and meshed after %.1f s%s", (ticks - sceneAt) / 20.0, idleTicks >= 100 ? "" : " TIMEOUT"));
                    idleTicks = 0;
                    startMeasuring();
                }
            }
            case RECORD -> {
                // a pending frame hash is taken here first: the teleport 3 s in used to win the race (09-28 hashes of
                // empty sky at 2000 / 140 / 2000 for every scene)
                if (since(phaseStart) > 3 && hashAt < 0) {
                    int[] p = TP[tpIndex];
                    // -Dsleep.clientAuto.travel=true: through dev.sleep.api.Travel (the destination loaded first, no stall)
                    command(mc, (Boolean.getBoolean("sleep.clientAuto.travel") ? "sleep travel " : "tp @s ") + p[0] + " 140 " + p[1]);
                    enter(Phase.TP_WAIT_START);
                }
            }
            case TP_WAIT_START -> {
                if (STOP_IN_TP >= 0 && tpIndex == TP.length - 1 && since(phaseStart) > STOP_IN_TP) {
                    log(String.format(Locale.ROOT, "stopping %d s into teleport %d (stopInTp: a shutdown while the server generates)", STOP_IN_TP, tpIndex + 1));
                    enter(Phase.DONE);
                    mc.stop();
                    return;
                }
                if ((STOP_IN_TP < 0 || tpIndex < TP.length - 1) && (queueBusy(mc) || since(phaseStart) > 5)) enter2Done();
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
                if (!ClientMeter.running()) {
                    if (recording != null) {
                        recording.stop();   // written to its destination
                        recording.close();
                        recording = null;
                    }
                    log(dev.sleep.optsupport.ClientCounters.report());
                    if (dev.sleep.optsupport.LightTiming.ON) log(dev.sleep.optsupport.LightTiming.report());
                    if (dev.sleep.optsupport.LightScout.ON) log(dev.sleep.optsupport.LightScout.report());
                    if (dev.sleep.optsupport.LightRecipe.VERIFY) log(dev.sleep.optsupport.LightRecipe.report());
                    if (Boolean.getBoolean("lab.shaderlight.diag")) log(dev.sleep.optsupport.LightVolumes.diagReport());
                    if (DrawStats.ON) log(DrawStats.report());
                    if (Boolean.getBoolean("lab.geoparts.verify")) log(dev.sleep.optsupport.GeoParts.report());   // GeckoLib loaded then
                    for (java.util.function.Function<String, String> r : REPORTS) {   // other mods' reports (the label of the run given)
                        String x = r.apply(LABEL);
                        if (x != null) log(x);
                    }
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
