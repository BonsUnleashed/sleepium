package dev.lull.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Where the settings screen opens (physical client only): the mod list's Config button, and a "Sleepium" button on the
 * game's Options screen (title screen and pause menu alike).
 */
public final class SleepUi {
    private SleepUi() {}

    public static void register(ModContainer container, IEventBus gameBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (c, parent) -> new SleepScreen(parent));
        gameBus.addListener(ScreenEvent.Init.Post.class, e -> {
            if (!(e.getScreen() instanceof OptionsScreen options)) return;
            Button b = Button.builder(Component.translatable("sleepium.ui.open"), x -> Minecraft.getInstance().setScreen(new SleepScreen(options)))
                    .bounds(6, 6, 64, 20).tooltip(Tooltip.create(Component.translatable("sleepium.ui.open.tooltip"))).build();
            e.addListener(b);
        });
        // a shaderpack being prepared: its progress in the corner, on menus and in the game alike
        gameBus.addListener(ScreenEvent.Render.Post.class, e -> preparing(e.getGuiGraphics()));
        gameBus.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class, e -> {
            if (Minecraft.getInstance().screen == null) preparing(e.getGuiGraphics());
        });
        if (SHOT != null) gameBus.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Post.class, e -> shots());
        if (SWITCH != null) gameBus.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Post.class, e -> switchTest());
    }

    private static void preparing(net.minecraft.client.gui.GuiGraphics g) {
        String p = dev.lull.shaders.ShaderPipeline.preparing();
        if (p == null) return;
        int sp = p.lastIndexOf(' ');
        g.drawString(Minecraft.getInstance().font, Component.translatable("sleepium.shaders.preparing", p.substring(0, sp), p.substring(sp + 1)), 4, 4, 0xFFE08A, true);
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
            if (i + 1 < at.length) mc.setScreen(new SleepScreen(((SleepScreen) mc.screen).parent(), i + 1));
            else {
                // -Dsleep.ui.saveTest=<key>: the screen's Done with that flag switched (config/sleepium.properties written)
                String key = System.getProperty("sleep.ui.saveTest");
                if (key != null && mc.screen instanceof SleepScreen s) s.probeToggleAndClose(key);
                mc.stop();
            }
        }
    }

    /**
     * -Dsleep.ui.switchTest=<pack>,<dir>: a dev probe of switching packs live in a world - the pack at 3 s, none at 12 s,
     * the pack again at 20 s; a screenshot before each switch and at 28 s
     */
    private static final String SWITCH = System.getProperty("sleep.ui.switchTest");
    private static long levelAt;
    private static int step;

    private static void switchTest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long now = System.nanoTime();
        if (levelAt == 0) levelAt = now;
        double t = (now - levelAt) / 1e9;
        double[] at = {3, 12, 20, 28};
        if (step >= at.length || t < at[step]) return;
        String[] a = SWITCH.split(",", 2);
        try (var img = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            java.nio.file.Path out = java.nio.file.Path.of(a[1]).resolve("switch-" + step + ".png");
            java.nio.file.Files.createDirectories(out.getParent());
            img.writeToFile(out);
        } catch (Exception ex) {
            System.out.println("[sleep/ui] shot: " + ex);
        }
        System.out.println("[sleep/ui] switch test step " + step + " at " + String.format("%.1f", t) + " s, pack " + dev.lull.shaders.ShaderPipeline.currentName());
        if (step == 0 || step == 2) dev.lull.shaders.ShaderPipeline.request(a[0]);
        else if (step == 1) dev.lull.shaders.ShaderPipeline.request(null);
        step++;
    }
}
