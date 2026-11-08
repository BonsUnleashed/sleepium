package dev.lull.optsupport;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Entities added to the live world from a thread other than the server's (OffThreadEntityAddMixin,
 * OffThreadEntityRegionMixin; -Dlab.opt.OffThreadEntity). Vanilla places a new chunk's starting mobs on a
 * world-generation worker, into the chunk's WorldGenRegion; a mod that reacts to those spawns (a FinalizeSpawn handler)
 * by calling ServerLevel.addFreshEntity from that worker writes the live entity storage from the wrong thread - a race
 * in vanilla too (Easy Mob Spawn Control, GitHub #4; Cupboard warns "add an entity from offthread").
 *
 * <p>Such an add goes where it was meant to: an entity inside the region being generated on this thread goes into that
 * region (it becomes part of the chunk, like vanilla's starting mobs); any other off-thread add is handed to the server
 * thread (server.execute) and reported as done. Adds on the server thread - every correct caller - are untouched. Each
 * calling class is named once in the log.
 */
public final class OffThreadEntity {
    private OffThreadEntity() {}

    /** the region whose starting mobs this thread is spawning (NaturalSpawner.spawnMobsForChunkGeneration) */
    private static final ThreadLocal<ServerLevelAccessor> REGION = new ThreadLocal<>();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    public static final LongAdder TO_REGION = new LongAdder(), TO_SERVER = new LongAdder();

    public static void enter(ServerLevelAccessor region) {
        REGION.set(region);
    }

    public static void exit() {
        REGION.remove();
    }

    /** an addFreshEntity on the live level from another thread: where the entity goes instead; the value reported */
    public static boolean add(ServerLevel level, Entity entity) {
        ServerLevelAccessor r = REGION.get();
        if (r instanceof WorldGenRegion region && region.getLevel() == level
                && region.hasChunk(SectionPos.blockToSectionCoord(entity.getBlockX()), SectionPos.blockToSectionCoord(entity.getBlockZ()))) {
            TO_REGION.increment();
            log("the chunk being generated");
            return region.addFreshEntity(entity);
        }
        TO_SERVER.increment();
        log("the server thread");
        level.getServer().execute(() -> level.addFreshEntity(entity));
        return true;
    }

    private static void log(String where) {
        String who = StackWalker.getInstance().walk(s -> s.map(StackWalker.StackFrame::getClassName)
                .filter(c -> !c.startsWith("net.minecraft.") && !c.startsWith("net.neoforged.") && !c.startsWith("dev.lull.") && !c.startsWith("dev.lab.")
                        && !c.startsWith("java.") && !c.startsWith("org.spongepowered.") && !c.startsWith("com.llamalad7."))
                .findFirst().orElse("?"));
        if (LOGGED.add(who))
            System.out.println("[sleep/opt] " + who + " added an entity to the world from " + Thread.currentThread().getName()
                    + " (not the server thread): it goes to " + where + " instead (OffThreadEntity)");
    }

    public static String report() {
        return "off-thread entity adds: into the generating chunk " + TO_REGION.sum() + ", handed to the server thread " + TO_SERVER.sum();
    }
}
