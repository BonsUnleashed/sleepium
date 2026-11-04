package dev.lull.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Where the settings screen opens (physical client only): the mod list's Config button, and a "Sleepium" button on the
 * game's Options screen (title screen and pause menu alike).
 * <p>
 * 1.20.1: the mod list's button is Forge's ConfigScreenFactory extension point (1.21.1: NeoForge's IConfigScreenFactory
 * on the mod container); frames are Forge's RenderTickEvent (START), the server tick its ServerTickEvent (END). The
 * shaderpack pipeline is not on 1.20.1 yet (port-pending.txt): its corner progress line, the pack options screen and
 * the pack probes (-Dsleep.ui.packApplyTest, -Dsleep.ui.switchTest, the shots' pack screens) come back with it.
 */
public final class SleepUi {
    private SleepUi() {}

    public static void register(IEventBus gameBus) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SleepScreen(parent)));
        gameBus.addListener(EventPriority.NORMAL, false, ScreenEvent.Init.Post.class, e -> {
            // the Options screen and the pause menu: a button at the top left (1.20.1's PauseScreen does not say whether it
            // shows its menu - F3+Esc hides it - but a hidden menu has no widgets)
            if (!(e.getScreen() instanceof OptionsScreen)
                    && !(e.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen && !e.getListenersList().isEmpty())) return;
            var screen = e.getScreen();
            Button b = Button.builder(Component.translatable("sleepium.ui.open"), x -> Minecraft.getInstance().setScreen(new SleepScreen(screen)))
                    .bounds(6, 6, 72, 20).tooltip(Tooltip.create(Component.translatable("sleepium.ui.open.tooltip"))).build();
            e.addListener(b);
        });
        // live numbers: frame times (every frame), the integrated server's tick and the sleep engine (its thread)
        gameBus.addListener(EventPriority.LOWEST, false, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.START) SleepStats.frame();
        });
        gameBus.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END) SleepStats.serverTick(e.getServer());
        });
        gameBus.addListener(EventPriority.NORMAL, false, RenderGuiEvent.Post.class, e -> {
            if (Minecraft.getInstance().screen == null) SleepStats.drawHud(e.getGuiGraphics());
        });
        if (SHOT != null) gameBus.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END) shots();
        });
        if (SHOT_WORLD != null) gameBus.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END) worldShots();
        });
    }

    /** -Dsleep.ui.shot=<dir>: a dev probe - opens the screen on the title screen, saves each tab to dir, quits */
    private static final String SHOT = System.getProperty("sleep.ui.shot");
    private static int shotFrame;

    private static void shots() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) && !(mc.screen instanceof SleepScreen)
                && !(mc.screen instanceof OptionsScreen) || mc.getOverlay() != null) return;   // (not while resources reload)
        int f = shotFrame++;
        String lang = System.getProperty("sleep.ui.lang");
        if (f == 10 && lang != null) {
            mc.getLanguageManager().setSelected(lang);
            mc.options.languageCode = lang;
            mc.reloadResourcePacks();
        }
        if (f == 40) mc.setScreen(new OptionsScreen(mc.screen, mc.options));
        if (f == 80) {
            try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                java.nio.file.Path out = java.nio.file.Path.of(SHOT).resolve("options.png");
                java.nio.file.Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (Exception ex) {
                System.out.println("[sleep/ui] shot: " + ex);
            }
        }
        if (f == 90) mc.setScreen(new SleepScreen(mc.screen, 0));
        // the overview, a group, the flags (1.21.1 also the shaders page and a pack's own options)
        int[] at = {120, 160, 200};
        for (int i = 0; i < at.length; i++) {
            if (f != at[i]) continue;
            try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                java.nio.file.Path out = java.nio.file.Path.of(SHOT).resolve("ui-" + i + ".png");
                java.nio.file.Files.createDirectories(out.getParent());
                img.writeToFile(out);
            } catch (Exception ex) {
                System.out.println("[sleep/ui] shot: " + ex);
            }
            if (i + 1 < at.length) mc.setScreen(new SleepScreen(((SleepScreen) mc.screen).parent(), i == 0 ? 1 : 3));
            else {
                // -Dsleep.ui.saveTest=<key>: the screen's Done with that flag switched (config/sleepium.properties written)
                String key = System.getProperty("sleep.ui.saveTest");
                if (key != null && mc.screen instanceof SleepScreen s) s.probeToggleAndClose(key);
                String preset = System.getProperty("sleep.ui.presetTest");
                if (preset != null && mc.screen instanceof SleepScreen s) s.probePresetAndClose(preset);
                mc.stop();
            }
        }
    }

    /** -Dsleep.ui.shotWorld=<dir>: in a world, the overview at 8 s and the corner stats at 14 s, saved to dir */
    private static final String SHOT_WORLD = System.getProperty("sleep.ui.shotWorld");
    private static long worldAt;
    private static int worldStep;

    private static void worldShots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (worldAt == 0) worldAt = now;
        double t = (now - worldAt) / 1e9;
        if (worldStep == 0 && t > 8) {
            mc.setScreen(new SleepScreen(null, 0));
            worldStep = 1;
        } else if (worldStep == 1 && t > 11) {
            shot(mc, "world-overview");
            mc.setScreen(null);
            SleepStats.hud = true;
            worldStep = 2;
        } else if (worldStep == 2 && t > 14) {
            shot(mc, "world-hud");
            worldStep = 3;
        }
    }

    private static void shot(Minecraft mc, String name) {
        try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            java.nio.file.Path out = java.nio.file.Path.of(SHOT_WORLD).resolve(name + ".png");
            java.nio.file.Files.createDirectories(out.getParent());
            img.writeToFile(out);
        } catch (Exception ex) {
            System.out.println("[sleep/ui] shot: " + ex);
        }
    }
}
