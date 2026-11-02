package dev.lull.ui;

import dev.lull.shaders.PackScreenModel;
import dev.lull.shaders.ShaderPipeline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A shaderpack's own settings, laid out as the pack asks (shaders.properties: screens, columns, sliders, profiles),
 * labelled from its language files. A button steps through an option's values (shift: backwards), a slider drags them;
 * [screens] open below, Back returns. Apply writes shaderpacks/<pack>.txt and, if the pack is on, prepares it again.
 */
public final class PackOptionsScreen extends Screen {
    private static final int TOP = 26, BOTTOM = 30, DESC_H = 42;
    private final Screen parent;
    private final PackScreenModel model;
    private final ArrayDeque<String> stack = new ArrayDeque<>();
    private String screen = "";
    private boolean dirty;
    private Grid grid;
    private Component hoverTip;

    public PackOptionsScreen(Screen parent, PackScreenModel model) {
        super(Component.literal(model.pack));
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        grid = new Grid(minecraft, width, height - TOP - BOTTOM - DESC_H, TOP, 24, Math.min(420, width - 24));
        addRenderableWidget(grid);
        fill();
        int cx = width / 2, bw = Math.min(100, (width - 40) / 4);
        addRenderableWidget(Button.builder(stack.isEmpty() ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_BACK, x -> back())
                .bounds(cx - 2 * bw - 6, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("sleepium.ui.packopts.reset"), x -> {
            model.resetAll();
            dirty = true;
            rebuildWidgets();
        }).bounds(cx - bw - 2, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("sleepium.ui.packopts.apply"), x -> apply()).bounds(cx + 2, height - 26, bw, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, x -> {
            apply();
            minecraft.setScreen(parent);
        }).bounds(cx + bw + 6, height - 26, bw, 20).build());
    }

    /** the dev probe (-Dsleep.ui.packApplyTest): an option set as its button would, then Apply */
    void probeSetAndApply(String option, String value) {
        model.set(option, value);
        dirty = true;
        apply();
    }

    /** the dev shots: open a screen of the pack as its button would */
    void probeScreen(String id) {
        stack.push(screen);
        screen = id;
        rebuildWidgets();
    }

    private void back() {
        if (stack.isEmpty()) {
            minecraft.setScreen(parent);   // Cancel: the file is not written
            return;
        }
        screen = stack.pop();
        rebuildWidgets();
    }

    private void apply() {
        if (!dirty) return;
        try {
            model.save();
        } catch (java.io.IOException e) {
            System.out.println("[sleep/ui] pack options: " + e);
        }
        dirty = false;
        // the pack on now: prepared again with the new values (vanilla drawn meanwhile)
        if (model.pack.equals(ShaderPipeline.currentName())) ShaderPipeline.request(model.pack);
    }

    private void fill() {
        grid.clear();
        List<String> entries = new ArrayList<>();
        for (String e : model.screens.getOrDefault(screen, List.of())) {
            if (e.equals("*")) entries.addAll(model.unplaced());
            else entries.add(e);
        }
        int cols = Math.max(1, Math.min(3, model.columns(screen)));
        int cellW = (grid.getRowWidth() - (cols - 1) * 6) / cols;
        List<AbstractWidget> row = new ArrayList<>();
        List<Component> tips = new ArrayList<>();
        for (String e : entries) {
            AbstractWidget w = widget(e, cellW);
            if (w == null && !e.equals("<empty>")) continue;   // an option this pack's settings left out
            row.add(w);
            tips.add(tip(e));
            if (row.size() == cols) {
                grid.add(new Cells(row, tips));
                row = new ArrayList<>();
                tips = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) grid.add(new Cells(row, tips));
    }

    private Component tip(String e) {
        String c;
        if (e.equals("<profile>")) c = model.profileComment();
        else if (e.startsWith("[")) c = model.screenComment(e.substring(1, e.length() - 1));
        else c = model.comment(e);
        return c == null ? null : Component.literal(c);
    }

    private AbstractWidget widget(String e, int w) {
        if (e.equals("<empty>")) return null;
        if (e.equals("<profile>")) {
            if (model.profiles.isEmpty()) return null;
            Button b = Button.builder(Component.empty(), x -> {
                List<String> names = model.profileNames();
                int i = names.indexOf(model.profile());
                int step = Screen.hasShiftDown() ? -1 : 1;
                model.applyProfile(names.get(Math.floorMod(i + step, names.size())));
                dirty = true;
            }).size(w, 20).build();
            entryText.put(b, () -> Component.translatable("sleepium.ui.packopts.profile", Component.literal(model.profileLabel(model.profile()))));
            return b;
        }
        if (e.startsWith("[") && e.endsWith("]")) {
            String id = e.substring(1, e.length() - 1);
            return Button.builder(Component.literal(model.screenLabel(id) + "..."), x -> {
                stack.push(screen);
                screen = id;
                rebuildWidgets();
            }).size(w, 20).build();
        }
        if (!model.isOption(e)) return null;
        List<String> values = model.values(e);
        if (model.isSlider(e) && values.size() > 1) return new Slider(e, w);
        Button b = Button.builder(Component.empty(), x -> {
            int i = values.indexOf(model.value(e));
            model.set(e, values.get(Math.floorMod(i + (Screen.hasShiftDown() ? -1 : 1), values.size())));
            dirty = true;
        }).size(w, 20).build();
        b.active = values.size() > 1;
        entryText.put(b, () -> text(e));
        return b;
    }

    /** "Label: value" (the pack's labels; ON / OFF for a switch) */
    private Component text(String option) {
        String v = model.value(option);
        String vl = model.valueLabel(option, v);
        Component value = vl != null ? Component.literal(vl)
                : Boolean.parseBoolean(v) ? CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN) : CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED);
        boolean changed = !v.equals(model.defaultValue(option));
        return Component.literal(model.label(option)).append(": ").append(value).withStyle(changed ? ChatFormatting.ITALIC : ChatFormatting.RESET);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoverTip = null;
        // the buttons' texts follow the model (a profile changes many at once)
        for (Cells c : grid.children()) c.refresh();
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.literal(model.screenLabel(screen)), width / 2, 9, 0xFFFFFF);
        int x0 = Math.max(10, width / 2 - grid.getRowWidth() / 2), y0 = height - BOTTOM - DESC_H + 4, w = Math.min(grid.getRowWidth(), width - 20);
        g.fill(x0 - 4, y0 - 3, x0 + w + 4, height - BOTTOM - 2, 0x50000000);
        Component desc = hoverTip != null ? hoverTip
                : Component.translatable(dirty ? "sleepium.ui.packopts.unsaved" : "sleepium.ui.packopts.hint").withStyle(dirty ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY);
        int y = y0;
        for (FormattedCharSequence line : font.split(desc, w)) {
            if (y > height - BOTTOM - 10) break;
            g.drawString(font, line, x0, y, 0xE0E0E0);
            y += 10;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ widgets

    private final class Slider extends AbstractSliderButton {
        private final String option;
        private final List<String> values;

        Slider(String option, int w) {
            super(0, 0, w, 20, Component.empty(), 0);
            this.option = option;
            this.values = model.values(option);
            sync();
        }

        void sync() {
            int i = Math.max(0, values.indexOf(model.value(option)));
            value = values.size() <= 1 ? 0 : (double) i / (values.size() - 1);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(text(option));
        }

        @Override
        protected void applyValue() {
            int i = (int) Math.round(value * (values.size() - 1));
            String v = values.get(i);
            if (!v.equals(model.value(option))) {
                model.set(option, v);
                dirty = true;
            }
        }
    }

    private static final class Grid extends ContainerObjectSelectionList<Cells> {
        private final int rowWidth;

        Grid(Minecraft mc, int width, int height, int y, int itemHeight, int rowWidth) {
            super(mc, width, height, y, itemHeight);
            this.rowWidth = rowWidth;
        }

        void add(Cells c) {
            addEntry(c);
        }

        void clear() {
            clearEntries();
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width / 2 + rowWidth / 2 + 8;
        }
    }

    /** one row of the pack's columns (null: an empty cell) */
    private final class Cells extends ContainerObjectSelectionList.Entry<Cells> {
        private final List<AbstractWidget> cells;
        private final List<Component> tips;
        private final List<AbstractWidget> live = new ArrayList<>();

        Cells(List<AbstractWidget> cells, List<Component> tips) {
            this.cells = cells;
            this.tips = tips;
            for (AbstractWidget w : cells) if (w != null) live.add(w);
        }

        void refresh() {
            for (int i = 0; i < cells.size(); i++) {
                AbstractWidget w = cells.get(i);
                if (w instanceof Slider s && !s.isFocused()) s.sync();
                else if (w instanceof Button b && entryText.get(b) != null) b.setMessage(entryText.get(b).get());
            }
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int cols = cells.size();
            int cellW = cols == 0 ? width : cells.stream().filter(java.util.Objects::nonNull).mapToInt(AbstractWidget::getWidth).max().orElse(width);
            for (int i = 0; i < cols; i++) {
                AbstractWidget w = cells.get(i);
                if (w == null) continue;
                w.setX(left + i * (cellW + 6));
                w.setY(top);
                w.render(g, mouseX, mouseY, partialTick);
                if (w.isHovered() && tips.get(i) != null) hoverTip = tips.get(i);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return live;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return live;
        }
    }

    /** a button's text as the model is now (options, the profile) */
    private final java.util.IdentityHashMap<Button, java.util.function.Supplier<Component>> entryText = new java.util.IdentityHashMap<>();
}
