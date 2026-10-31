package dev.sleep.optsupport;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongConsumer;

/**
 * ServerReactor (light notifications): ServerChunkCache.onLightUpdate posts one main-thread task per changed light
 * section (a lambda, a queue node, an unpark) that sets a bit in the section's ChunkHolder and marks the chunk unsaved —
 * ~33k tasks a second while generating, ~330 per chunk. Here notifications go into a set of pending sections (sky and
 * block kept apart); the first one after a drain posts a single drain task; the drain applies each pending section once.
 * Applying the same section twice sets the same bit twice, so collapsing duplicates changes nothing; the notifications
 * of one batch run together where the first of them would have run.
 *
 * <p>Producers: the level's light actor (one thread at a time); a lock guards the two sets for the rare other caller.
 */
public final class LightNotifyRing {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.MainReactor");
    public static final LongAdder EVENTS = new LongAdder(), APPLIED = new LongAdder(), DRAINS = new LongAdder();

    private final Object lock = new Object();
    private LongOpenHashSet sky = new LongOpenHashSet(), block = new LongOpenHashSet();
    private LongOpenHashSet skySpare = new LongOpenHashSet(), blockSpare = new LongOpenHashSet();
    private boolean scheduled;

    /** any thread: note a section's light change; true if the caller must post a drain task */
    public boolean note(boolean skyLayer, long sectionPos) {
        EVENTS.increment();
        synchronized (lock) {
            (skyLayer ? sky : block).add(sectionPos);
            if (scheduled) return false;
            scheduled = true;
            return true;
        }
    }

    /** main thread: apply every pending section (sky first, then block) */
    public void drain(LongConsumer skyApply, LongConsumer blockApply) {
        LongOpenHashSet s, b;
        synchronized (lock) {
            s = sky;
            b = block;
            sky = skySpare;
            block = blockSpare;
            scheduled = false;
        }
        DRAINS.increment();
        var it = s.iterator();
        while (it.hasNext()) {
            skyApply.accept(it.nextLong());
            APPLIED.increment();
        }
        it = b.iterator();
        while (it.hasNext()) {
            blockApply.accept(it.nextLong());
            APPLIED.increment();
        }
        s.clear();
        b.clear();
        synchronized (lock) {
            skySpare = s;
            blockSpare = b;
        }
    }

    public static String report() {
        return String.format(java.util.Locale.ROOT, "light notifications: %d events, %d applied, %d drain tasks",
                EVENTS.sumThenReset(), APPLIED.sumThenReset(), DRAINS.sumThenReset());
    }
}
