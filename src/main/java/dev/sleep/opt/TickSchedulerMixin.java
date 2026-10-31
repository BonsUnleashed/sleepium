package dev.sleep.opt;

import dev.sleep.optsupport.LabScheduler;
import dev.sleep.optsupport.OptCounters;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Queue;
import java.util.function.LongPredicate;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/tick-scheduler: scheduled block and fluid ticks (vanilla and every mod) through the lab's time queue.
 *
 * <p>Vanilla keeps nextTickForContainer (chunk -> trigger time of its first tick) and, every tick, walks the whole map
 * to find the chunks that are due. Here every write of that map also puts a wake-up in a LabScheduler, and the walk
 * becomes "take the wake-ups that are due": stale ones (the chunk's time moved or it was removed) are recognised by
 * comparing with the map, which stays the source of truth. The per-entry logic is vanilla's (drop empty or missing
 * containers, move a later first tick, take ticking chunks, re-check non-ticking ones next tick), and the drain that
 * follows is untouched, so the ticks run in the same order. {@code -Dlab.opt.tickSchedCheck=true} compares the set of
 * chunks taken with vanilla's full walk every tick.
 */
@Mixin(LevelTicks.class)
public abstract class TickSchedulerMixin<T> {
    @Shadow @Final private LongPredicate tickCheck;
    @Shadow @Final private Long2ObjectMap<LevelChunkTicks<T>> allContainers;
    @Shadow @Final private Long2LongMap nextTickForContainer;
    @Shadow @Final private Queue<LevelChunkTicks<T>> containersToTick;

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.tickSchedCheck");
    /** a foreign mixin that writes the schedule elsewhere could hide wake-ups: then vanilla's walk */
    @Unique private static final boolean ACTIVE = probe$untouched();

    @Unique
    private static boolean probe$untouched() {
        String c = "net/minecraft/world/ticks/LevelTicks";
        for (String m : new String[] {"sortContainersToTick", "addContainer", "removeContainer", "updateContainerScheduling", "drainContainers",
                "rescheduleLeftoverContainers", "clearArea", "collectTicks", "schedule"})
            if (dev.sleep.optsupport.MixinTouch.touched(c, m)) return false;
        return true;
    }
    @Unique private final LabScheduler probe$wakes = new LabScheduler();
    @Unique private final LongOpenHashSet probe$seen = new LongOpenHashSet();

    /** every write of the container schedule also queues a wake-up at that time */
    @Redirect(method = {"addContainer", "updateContainerScheduling"},
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2LongMap;put(JJ)J"))
    private long probe$put(Long2LongMap map, long key, long time) {
        long prev = map.put(key, time);
        probe$wakes.add(time, key);
        return prev;
    }

    /**
     * @author probe (lab.opt.TickSchedulerMixin)
     * @reason due chunks from the time queue instead of a walk over every chunk with a pending tick
     */
    @Overwrite
    private void sortContainersToTick(long gameTime) {
        if (!ACTIVE) {
            probe$vanillaSort(gameTime);
            return;
        }
        LongOpenHashSet expected = CHECK ? probe$vanillaDue(gameTime) : null;
        LongOpenHashSet seen = probe$seen;
        seen.clear();
        LabScheduler wakes = probe$wakes;
        for (long key; (key = wakes.takeDue(gameTime)) != LabScheduler.NONE; ) {
            if (!seen.add(key)) continue;
            long j = nextTickForContainer.get(key);   // default Long.MAX_VALUE: removed
            if (j > gameTime) continue;               // stale: a later wake-up is queued, or none is needed
            LevelChunkTicks<T> c = allContainers.get(key);
            if (c == null) {
                nextTickForContainer.remove(key);
                continue;
            }
            ScheduledTick<T> first = c.peek();
            if (first == null) {
                nextTickForContainer.remove(key);
            } else if (first.triggerTick() > gameTime) {
                nextTickForContainer.put(key, first.triggerTick());
                wakes.add(first.triggerTick(), key);
            } else if (tickCheck.test(key)) {
                nextTickForContainer.remove(key);
                containersToTick.add(c);
            } else {
                wakes.add(gameTime + 1, key);   // vanilla keeps the entry and looks again next tick
            }
        }
        wakes.endTick(gameTime);   // later writes for times up to now are due next tick, as vanilla's walk would see them
        if (CHECK) probe$check(expected);
    }

    /** vanilla sortContainersToTick, verbatim */
    @Unique
    private void probe$vanillaSort(long gameTime) {
        ObjectIterator<Long2LongMap.Entry> it = Long2LongMaps.fastIterator(nextTickForContainer);
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            long i = entry.getLongKey();
            long j = entry.getLongValue();
            if (j <= gameTime) {
                LevelChunkTicks<T> c = allContainers.get(i);
                if (c == null) {
                    it.remove();
                } else {
                    ScheduledTick<T> first = c.peek();
                    if (first == null) {
                        it.remove();
                    } else if (first.triggerTick() > gameTime) {
                        entry.setValue(first.triggerTick());
                    } else if (tickCheck.test(i)) {
                        it.remove();
                        containersToTick.add(c);
                    }
                }
            }
        }
    }

    /** What vanilla's walk would take (read only), for the shadow check. */
    @Unique
    private LongOpenHashSet probe$vanillaDue(long gameTime) {
        LongOpenHashSet due = new LongOpenHashSet();
        ObjectIterator<Long2LongMap.Entry> it = Long2LongMaps.fastIterator(nextTickForContainer);
        while (it.hasNext()) {
            Long2LongMap.Entry e = it.next();
            if (e.getLongValue() > gameTime) continue;
            LevelChunkTicks<T> c = allContainers.get(e.getLongKey());
            if (c == null) continue;
            ScheduledTick<T> first = c.peek();
            if (first != null && first.triggerTick() <= gameTime && tickCheck.test(e.getLongKey())) due.add(e.getLongKey());
        }
        return due;
    }

    @Unique
    private void probe$check(LongOpenHashSet expected) {
        LongOpenHashSet got = new LongOpenHashSet();   // containersToTick is empty when the sort starts (cleanup)
        for (LevelChunkTicks<T> c : containersToTick) {
            ScheduledTick<T> first = c.peek();
            if (first != null) got.add(net.minecraft.world.level.ChunkPos.asLong(first.pos()));
        }
        OptCounters.TICKSCHED_CHECKED.incrementAndGet();
        if (!got.equals(expected) && OptCounters.TICKSCHED_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] tick scheduler MISMATCH: vanilla " + expected.size() + " chunks, scheduler " + got.size());
    }
}
