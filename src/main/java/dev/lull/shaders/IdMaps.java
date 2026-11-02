package dev.lull.shaders;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A pack's block.properties / entity.properties / item.properties (preprocessed with the standard and option macros):
 * {@code block.<id>=[ns:]name[:prop=v1,v2...] %tag ...}, {@code entity.<id>=names}, {@code item.<id>=names}. Explicit
 * block entries win over tag entries (Iris 1.8+); the first entry for a state wins. Values the vertices / uniforms get:
 * a block's id or -1 (unlisted), an entity's id or 65535 when the file exists but does not list it (0 without a file),
 * an item's id or -1.
 */
public final class IdMaps {
    public final Reference2IntOpenHashMap<BlockState> blocks = new Reference2IntOpenHashMap<>();
    public final Reference2IntOpenHashMap<EntityType<?>> entities = new Reference2IntOpenHashMap<>();
    public final Reference2IntOpenHashMap<Item> items = new Reference2IntOpenHashMap<>();
    /** names that are no registry entry (current_player, entity_shadow, name_tag, ...) */
    public final Object2IntOpenHashMap<String> special = new Object2IntOpenHashMap<>();
    public boolean hasBlocks, hasEntities, hasItems;
    public final List<String> unknown = new ArrayList<>();

    public IdMaps() {
        blocks.defaultReturnValue(-1);
        entities.defaultReturnValue(-2);
        items.defaultReturnValue(-1);
        special.defaultReturnValue(-2);
    }

    public static IdMaps load(ShaderPackSource src, List<String> macros) {
        IdMaps m = new IdMaps();
        Map<String, String> b = read(src, "block.properties", macros), e = read(src, "entity.properties", macros), i = read(src, "item.properties", macros);
        m.hasBlocks = b != null;
        m.hasEntities = e != null;
        m.hasItems = i != null;
        if (b != null) {
            Reference2IntOpenHashMap<BlockState> fromTags = new Reference2IntOpenHashMap<>();
            fromTags.defaultReturnValue(-1);
            for (var en : b.entrySet()) {
                if (!en.getKey().startsWith("block.")) continue;
                int id = parseId(en.getKey().substring(6));
                if (id < 0) continue;
                for (String entry : en.getValue().trim().split("\\s+")) if (!entry.isEmpty()) m.addBlock(entry, id, fromTags);
            }
            for (var t : fromTags.reference2IntEntrySet()) if (!m.blocks.containsKey(t.getKey())) m.blocks.put(t.getKey(), t.getIntValue());
        }
        if (e != null) {
            for (var en : e.entrySet()) {
                if (!en.getKey().startsWith("entity.")) continue;
                int id = parseId(en.getKey().substring(7));
                if (id < 0) continue;
                for (String name : en.getValue().trim().split("\\s+")) {
                    if (name.isEmpty()) continue;
                    ResourceLocation rl = ResourceLocation.tryParse(name);
                    var type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
                    if (type != null) m.entities.putIfAbsent(type, id);
                    else m.special.putIfAbsent(name, id);
                }
            }
        }
        if (i != null) {
            for (var en : i.entrySet()) {
                if (!en.getKey().startsWith("item.")) continue;
                int id = parseId(en.getKey().substring(5));
                if (id < 0) continue;
                for (String name : en.getValue().trim().split("\\s+")) {
                    if (name.isEmpty()) continue;
                    ResourceLocation rl = ResourceLocation.tryParse(name);
                    var item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                    if (item != null) m.items.putIfAbsent(item, id);
                    else m.special.putIfAbsent(name, id);
                }
            }
        }
        return m;
    }

    private static int parseId(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void addBlock(String entry, int id, Reference2IntOpenHashMap<BlockState> fromTags) {
        if (entry.startsWith("%")) {
            ResourceLocation rl = ResourceLocation.tryParse(entry.substring(1));
            if (rl == null) return;
            var tag = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl));
            if (tag.isEmpty()) return;
            for (var h : tag.get()) for (BlockState s : h.value().getStateDefinition().getPossibleStates()) fromTags.putIfAbsent(s, id);
            return;
        }
        String[] parts = entry.split(":");
        String name;
        int at;
        if (parts.length >= 2 && !parts[1].contains("=")) {
            name = parts[0] + ":" + parts[1];
            at = 2;
        } else {
            name = parts[0];
            at = 1;
        }
        ResourceLocation rl = ResourceLocation.tryParse(name);
        Block block = rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
        if (block == null) {
            if (unknown.size() < 50) unknown.add(entry);
            return;
        }
        Map<String, List<String>> cond = new LinkedHashMap<>();
        for (int k = at; k < parts.length; k++) {
            int eq = parts[k].indexOf('=');
            if (eq < 0) continue;
            cond.put(parts[k].substring(0, eq), List.of(parts[k].substring(eq + 1).split(",")));
        }
        states:
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            for (var c : cond.entrySet()) {
                Property<?> p = block.getStateDefinition().getProperty(c.getKey());
                if (p == null || !c.getValue().contains(valueName(s, p))) continue states;
            }
            blocks.putIfAbsent(s, id);
        }
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /** a pack .properties file, preprocessed, as key -> value (null when absent) */
    static Map<String, String> read(ShaderPackSource src, String path, List<String> macros) {
        if (src.text(path) == null) return null;
        GlslPreprocessor pp = new GlslPreprocessor(src::text, macros, l -> l);
        String[] lines = pp.process(path).split("\n");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip();
            while (l.endsWith("\\") && i + 1 < lines.length) l = l.substring(0, l.length() - 1) + " " + lines[++i].strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            String k = l.substring(0, eq).strip(), v = l.substring(eq + 1).strip();
            out.merge(k, v, (a, b) -> a + " " + b);   // a key twice (a block id split over lines): both lists
        }
        return out;
    }

    // ------------------------------------------------------------------ values

    public int block(BlockState s) {
        return blocks.getInt(s);
    }

    /** entityId for an entity type (special: the camera player's own id when the pack lists current_player) */
    public int entity(EntityType<?> type, boolean currentPlayer) {
        if (!hasEntities) return 0;
        if (currentPlayer) {
            int c = special.getInt("current_player");
            if (c >= 0) return c;
        }
        int id = entities.getInt(type);
        return id >= 0 ? id : 65535;
    }

    /** currentRenderedItemId / heldItemId: an item's id; a block item falls back to its block's id */
    public int item(Item item) {
        int id = items.getInt(item);
        if (id >= 0) return id;
        if (item instanceof net.minecraft.world.item.BlockItem bi) {
            int b = blocks.getInt(bi.getBlock().defaultBlockState());
            if (b >= 0) return b;
        }
        return -1;
    }
}
