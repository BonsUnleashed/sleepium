package dev.lull.optsupport;

import dev.lull.api.SideEffectJournal;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * ServerReactor (chunk loads): ChunkSerializer.read as a pool task over the saved tag (a private copy: IOWorker hands
 * out copies), with the main-thread notification carrying the result. The read is vanilla's own method, run inside a
 * {@link SideEffectJournal}: the calls that touch shared state — POI consistency, light data for the light engine, the
 * misplaced-chunk report, the ChunkDataEvent.Load post (mod listeners) — are recorded instead of run
 * (ReactorReadJournalMixin). The main thread replays them in the order vanilla ran them, then publishes the chunk; a
 * read that failed replays what it had recorded and rethrows there, so vanilla's failure handler sees the same state
 * and the same exception.
 */
public final class ReadJournal {
    public static final boolean ENABLED = !Boolean.getBoolean("lab.reactor.mainRead");
    public static final LongAdder POOL = new LongAdder(), ON_MAIN = new LongAdder(), ACTIONS = new LongAdder(), PUBLISH_NANOS = new LongAdder();

    private final SideEffectJournal.Recorded<ProtoChunk> recorded;

    private ReadJournal(SideEffectJournal.Recorded<ProtoChunk> recorded) {
        this.recorded = recorded;
    }

    /** pool: run the read, recording its shared-state calls */
    public static ReadJournal read(Supplier<ProtoChunk> read) {
        ReadJournal j = new ReadJournal(SideEffectJournal.run(read));
        POOL.increment();
        return j;
    }

    /** inside the read: record this call if a journal is open on this thread; false = run it now */
    public static boolean defer(Runnable call) {
        return SideEffectJournal.defer(call);
    }

    /** main thread: replay the recorded calls in order, then the read's result */
    public ProtoChunk publish() {
        long t = System.nanoTime();
        ACTIONS.add(recorded.calls());
        try {
            return recorded.publish();
        } finally {
            PUBLISH_NANOS.add(System.nanoTime() - t);
        }
    }

    public static String report() {
        long n = POOL.sumThenReset();
        return String.format(java.util.Locale.ROOT, "chunk reads: %d on the pool (%d replayed calls, %.1f us each on main), %d on main",
                n, ACTIONS.sumThenReset(), n == 0 ? 0 : PUBLISH_NANOS.sumThenReset() / 1e3 / n, ON_MAIN.sumThenReset());
    }
}
