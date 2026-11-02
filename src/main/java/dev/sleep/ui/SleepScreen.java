package dev.sleep.ui;

import dev.sleep.optsupport.SleepConfig;
import dev.sleep.shaders.ShaderPipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The sleep engine's settings: features (each a group of engine flags, switched together), the shaderpack (switched
 * live), and every flag one by one with search. Changes go to config/sleep.properties on Done; flags read at start
 * say "restart" until the game restarts. Flags given on the launch line are shown locked.
 */
public final class SleepScreen extends Screen {
    private static final int TAB_FEATURES = 0, TAB_SHADERS = 1, TAB_FLAGS = 2;
    private static int tab = TAB_FEATURES;   // remembered while the game runs
    private final Screen parent;
    private final Map<String, Boolean> desired = new HashMap<>();
    private String pack;
    private final String packAtOpen;
    private String filter = "", groupFilter;
    private OptList list;

    /** opened on a tab (the dev shots) */
    SleepScreen(Screen parent, int tab) {
        this(parent);
        SleepScreen.tab = tab;
    }

    Screen parent() {
        return parent;
    }

    public SleepScreen(Screen parent) {
        super(Component.translatable("sleep.ui.title"));
        this.parent = parent;
        for (SleepOptions.Option o : SleepOptions.all()) desired.put(o.key(), Boolean.parseBoolean(System.getProperty(o.key())));
        pack = ShaderPipeline.currentName();
        if (pack == null) {
            String p = System.getProperty("sleep.shaders");
            pack = p == null || p.isBlank() ? null : p;
        }
        packAtOpen = pack;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int cx = width / 2;
        String[] tabs = {"sleep.ui.tab.features", "sleep.ui.tab.shaders", "sleep.ui.tab.flags"};
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            Button b = Button.builder(Component.translatable(tabs[i]), x -> {
                tab = t;
                if (t != TAB_FLAGS) groupFilter = null;
                rebuildWidgets();
            }).bounds(cx - 155 + i * 105, 22, 100, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        int listTop = 48;
        if (tab == TAB_FLAGS) {
            EditBox search = new EditBox(font, cx - 100, 48, 200, 18, Component.translatable("sleep.ui.search"));
            search.setHint(Component.translatable("sleep.ui.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(filter);
            search.setResponder(s -> {
                filter = s;
                fill();
            });
            addRenderableWidget(search);
            listTop = 72;
        }
        list = new OptList(minecraft, width, height - listTop - 56, listTop);
        addRenderableWidget(list);
        fill();
        addRenderableWidget(Button.builder(Component.translatable("sleep.ui.reset"), x -> {
            for (SleepOptions.Option o : SleepOptions.all()) if (!SleepConfig.launchFlag(o.key())) desired.put(o.key(), o.byDefault());
            fill();
        }).bounds(cx - 155, height - 28, 150, 20).tooltip(Tooltip.create(Component.translatable("sleep.ui.reset.tooltip"))).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, x -> onClose()).bounds(cx + 5, height - 28, 150, 20).build());
    }

    private void fill() {
        list.clear();
        switch (tab) {
            case TAB_FEATURES -> fillFeatures();
            case TAB_SHADERS -> fillShaders();
            default -> fillFlags();
        }
    }

    private void fillFeatures() {
        for (var e : SleepOptions.byGroup().entrySet()) {
            // a feature is its tested flags; the experimental ones (off by default) are switched one by one under All flags
            List<SleepOptions.Option> opts = e.getValue().stream().filter(SleepOptions.Option::byDefault).toList();
            if (opts.isEmpty()) continue;
            String g = e.getKey();
            Button toggle = Button.builder(Component.empty(), x -> {
                boolean allOn = opts.stream().filter(o -> !SleepConfig.launchFlag(o.key())).allMatch(o -> desired.get(o.key()));
                for (SleepOptions.Option o : opts) if (!SleepConfig.launchFlag(o.key())) desired.put(o.key(), !allOn);
            }).size(70, 20).build();
            Button details = Button.builder(Component.translatable("sleep.ui.details"), x -> {
                tab = TAB_FLAGS;
                groupFilter = g;
                filter = "";
                rebuildWidgets();
            }).size(60, 20).build();
            Component label = Component.translatable("sleep.ui.group." + g);
            list.add(new Row(label, () -> {
                long on = opts.stream().filter(o -> desired.get(o.key())).count();
                toggle.setMessage(on == opts.size() ? on(true) : on == 0 ? on(false)
                        : Component.translatable("sleep.ui.partly").withStyle(ChatFormatting.YELLOW));
                return Component.translatable("sleep.ui.count", on, opts.size()).withStyle(ChatFormatting.GRAY);
            }, Component.translatable("sleep.ui.group." + g + ".desc"), toggle, details));
        }
    }

    private void fillFlags() {
        String f = filter.toLowerCase(Locale.ROOT);
        for (SleepOptions.Option o : SleepOptions.all()) {
            if (groupFilter != null && !groupFilter.equals(o.group())) continue;
            if (!f.isEmpty() && !o.key().toLowerCase(Locale.ROOT).contains(f) && !o.description().toLowerCase(Locale.ROOT).contains(f)) continue;
            boolean locked = SleepConfig.launchFlag(o.key());
            Button toggle = Button.builder(Component.empty(), x -> desired.put(o.key(), !desired.get(o.key()))).size(70, 20).build();
            toggle.active = !locked;
            MutableComponent tip = Component.literal(o.key()).withStyle(ChatFormatting.GRAY).append("\n")
                    .append(o.description().isEmpty() ? Component.translatable("sleep.ui.nodesc") : Component.literal(o.description())).append("\n")
                    .append(Component.translatable(o.byDefault() ? "sleep.ui.default.on" : "sleep.ui.default.off").withStyle(ChatFormatting.DARK_GRAY));
            if (locked) tip.append("\n").append(Component.translatable("sleep.ui.locked", o.key()).withStyle(ChatFormatting.GOLD));
            MutableComponent label = Component.literal(o.shortName());
            if (!o.byDefault()) label.append(Component.translatable("sleep.ui.experimental").withStyle(ChatFormatting.DARK_AQUA));
            list.add(new Row(label, () -> {
                toggle.setMessage(on(desired.get(o.key())));
                return restartNeeded(o.key()) ? Component.translatable("sleep.ui.restart.short").withStyle(ChatFormatting.YELLOW) : null;
            }, tip, toggle));
        }
    }

    private void fillShaders() {
        Path dir = minecraft.gameDirectory.toPath().resolve("shaderpacks");
        List<String> packs = new ArrayList<>();
        try (Stream<Path> s = Files.isDirectory(dir) ? Files.list(dir) : Stream.empty()) {
            s.filter(p -> Files.isDirectory(p) || p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .map(p -> p.getFileName().toString()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(packs::add);
        } catch (IOException e) {
            System.out.println("[sleep/ui] shaderpacks: " + e);
        }
        list.add(packRow(null, Component.translatable("sleep.ui.shaders.none")));
        for (String p : packs) list.add(packRow(p, Component.literal(p)));
        Button folder = Button.builder(Component.translatable("sleep.ui.shaders.folder"), x -> {
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {
            }
            Util.getPlatform().openPath(dir);
        }).size(150, 20).build();
        list.add(new Row(packs.isEmpty() ? Component.translatable("sleep.ui.shaders.empty").withStyle(ChatFormatting.GRAY) : Component.empty(), null,
                Component.translatable("sleep.ui.shaders.note"), folder));
    }

    private Row packRow(String name, Component label) {
        Button use = Button.builder(Component.empty(), x -> {
            pack = name;
            ShaderPipeline.request(name);   // live: from the next frame, the chunks rebuilt for it
        }).size(90, 20).build();
        return new Row(label, () -> {
            boolean selected = name == null ? pack == null : name.equals(pack);
            use.active = !selected;
            use.setMessage(selected ? Component.translatable("sleep.ui.shaders.selected").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("sleep.ui.shaders.use"));
            return null;
        }, Component.translatable("sleep.ui.shaders.note"), use);
    }

    private static Component on(boolean on) {
        return on ? CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN) : CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED);
    }

    private boolean restartNeeded(String key) {
        return desired.get(key) != Boolean.parseBoolean(SleepConfig.startup(key));
    }

    // ------------------------------------------------------------------ drawing, closing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        long n = SleepOptions.all().stream().filter(o -> restartNeeded(o.key())).count();
        if (n > 0) g.drawCenteredString(font, Component.translatable("sleep.ui.restart", n), width / 2, height - 44, 0xFFFF55);
        else if (tab == TAB_SHADERS && pack != null && !pack.equals(ShaderPipeline.currentName()) && !pack.equals(packAtOpen))
            g.drawCenteredString(font, Component.translatable("sleep.ui.shaders.loading"), width / 2, height - 44, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        save();
        minecraft.setScreen(parent);
    }

    /** the file keeps what differs from the defaults (or from what this run started with: dev runs set no defaults) */
    private void save() {
        Map<String, String> out = new TreeMap<>(SleepConfig.saved());
        for (SleepOptions.Option o : SleepOptions.all()) {
            boolean v = desired.get(o.key());
            if (v != o.byDefault() || v != Boolean.parseBoolean(SleepConfig.startup(o.key()))) out.put(o.key(), Boolean.toString(v));
            else out.remove(o.key());
        }
        if (pack != null) out.put("sleep.shaders", pack);
        else out.remove("sleep.shaders");
        SleepConfig.save(out);
    }

    // ------------------------------------------------------------------ the list

    private static final class OptList extends ContainerObjectSelectionList<Row> {
        OptList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, 24);
        }

        void add(Row r) {
            addEntry(r);
        }

        void clear() {
            clearEntries();
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return Math.min(420, width - 40);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }
    }

    /** a label (and a status line computed each frame, which also updates the buttons), its tooltip, buttons at the right */
    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final Component label, tip;
        private final java.util.function.Supplier<Component> status;
        private final List<AbstractWidget> widgets;

        Row(Component label, java.util.function.Supplier<Component> status, Component tip, AbstractWidget... widgets) {
            this.label = label;
            this.status = status;
            this.tip = tip;
            this.widgets = List.of(widgets);
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            Component st = status == null ? null : status.get();
            int x = left + width;
            for (int i = widgets.size() - 1; i >= 0; i--) {
                AbstractWidget w = widgets.get(i);
                x -= w.getWidth();
                w.setX(x);
                w.setY(top);
                w.render(g, mouseX, mouseY, partialTick);
                x -= 4;
            }
            int textRight = x - 4;
            g.drawString(font, label, left + 2, top + 6, 0xFFFFFF);
            if (st != null) g.drawString(font, st, textRight - font.width(st), top + 6, 0xAAAAAA);
            if (tip != null && hovering && mouseX < textRight) setTooltipForNextRenderPass(tip);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
