package dev.sleep.api;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Opt-outs for mods whose objects must keep vanilla's path. probe proves what it can by itself (a renderer that
 * animates by time leaves the render cache, a block entity whose tick changes state never sleeps), but a mod knows
 * things no observation can see — a renderer with a thread-affine resource, a tick that talks to an external system.
 * Register during mod construction or common setup, before the first world loads: some decisions are made once per
 * class and kept.
 *
 * <p>Players can do the same without code, as JVM properties with comma-separated ids:
 * {@code -Dprobe.compat.noParallelRender=}, {@code noRenderCache}, {@code noSleep} (block entity types),
 * {@code noEntitySleep} (entity types), e.g. {@code -Dprobe.compat.noRenderCache=create:shaft,mymod:clock}.
 */
public final class Compat {
    private static final Set<Object> NO_PARALLEL_RENDER = ConcurrentHashMap.newKeySet(), NO_RENDER_CACHE = ConcurrentHashMap.newKeySet(),
            NO_SLEEP = ConcurrentHashMap.newKeySet(), NO_ENTITY_SLEEP = ConcurrentHashMap.newKeySet();
    /** false while nothing is registered: the checks below then cost one volatile read */
    private static volatile boolean any;
    private static volatile boolean idsResolved;

    private Compat() {}

    /** this entity type always renders on the render thread (not on ParallelRender's workers) */
    public static void excludeFromParallelRender(EntityType<?> type) {
        add(NO_PARALLEL_RENDER, type);
    }

    /** this entity type is always rendered the vanilla way, never replayed from the render cache */
    public static void excludeFromRenderCache(EntityType<?> type) {
        add(NO_RENDER_CACHE, type);
    }

    /** this block entity type is always rendered the vanilla way, never replayed from the render cache */
    public static void excludeFromRenderCache(BlockEntityType<?> type) {
        add(NO_RENDER_CACHE, type);
    }

    /** this block entity type always ticks (never put to sleep by the block-entity sleep engine) */
    public static void excludeFromSleep(BlockEntityType<?> type) {
        add(NO_SLEEP, type);
    }

    /** this entity type always ticks (never put to sleep by the entity sleep engine) */
    public static void excludeFromEntitySleep(EntityType<?> type) {
        add(NO_ENTITY_SLEEP, type);
    }

    // ------------------------------------------------------------------ checks (probe internals)

    public static boolean parallelRenderExcluded(Entity e) {
        return any && check(NO_PARALLEL_RENDER, e.getType());
    }

    /** obj: an Entity or a BlockEntity */
    public static boolean renderCacheExcluded(Object obj) {
        if (!any) return false;
        if (obj instanceof Entity e) return check(NO_RENDER_CACHE, e.getType());
        if (obj instanceof BlockEntity be) return check(NO_RENDER_CACHE, be.getType());
        return false;
    }

    public static boolean sleepExcluded(BlockEntity be) {
        return any && check(NO_SLEEP, be.getType());
    }

    public static boolean entitySleepExcluded(Entity e) {
        return any && check(NO_ENTITY_SLEEP, e.getType());
    }

    // ------------------------------------------------------------------

    static {
        any = !prop("noParallelRender").isEmpty() || !prop("noRenderCache").isEmpty() || !prop("noSleep").isEmpty() || !prop("noEntitySleep").isEmpty();
    }

    private static void add(Set<Object> set, Object type) {
        set.add(type);
        any = true;
    }

    private static boolean check(Set<Object> set, Object type) {
        if (!idsResolved) resolveIds();
        return set.contains(type);
    }

    /** the property ids, looked up once the registries are there (first check happens in a world) */
    private static synchronized void resolveIds() {
        if (idsResolved) return;
        for (String id : prop("noParallelRender")) BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).ifPresent(NO_PARALLEL_RENDER::add);
        for (String id : prop("noRenderCache")) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(NO_RENDER_CACHE::add);
            BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(rl).ifPresent(NO_RENDER_CACHE::add);
        }
        for (String id : prop("noSleep")) BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).ifPresent(NO_SLEEP::add);
        for (String id : prop("noEntitySleep")) BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id)).ifPresent(NO_ENTITY_SLEEP::add);
        idsResolved = true;
    }

    private static java.util.List<String> prop(String name) {
        String v = System.getProperty("probe.compat." + name, "").trim();
        if (v.isEmpty()) return java.util.List.of();
        return java.util.Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
