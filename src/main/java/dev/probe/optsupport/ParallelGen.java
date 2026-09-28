package dev.probe.optsupport;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/**
 * exp/parallel-worldgen: the worldgen actor schedules, the pool computes.
 *
 * <p>Vanilla already has one owner of generation state: the `worldgen` mailbox runs every ChunkGenerationTask, which
 * walks ChunkPyramid layer by layer (acquireStatusBump makes each (chunk, status) step run once). But it also runs
 * SURFACE, CARVERS and FEATURES inline, one chunk at a time for the whole level. Here those step bodies go to the
 * server's one background pool (Util.backgroundExecutor, the pool NOISE and BIOMES already use; a LabRuntime with
 * LabPoolsMixin), so the actor only schedules. No pool of our own: one set of workers sized to the machine.
 *
 * <p>Write radii from ChunkPyramid:
 * <ul>
 *   <li>SURFACE, CARVERS: 0, they write only their own chunk → any number at once;</li>
 *   <li>FEATURES: 1, it writes the 3x3 neighbourhood → a job claims its 9 chunks in a claims table. A job whose window
 *       overlaps a running one does not block a worker: it waits in a FIFO and is started by the job that frees its
 *       window. Earlier waiters reserve their windows during a scan, so a later job cannot overtake them forever.</li>
 * </ul>
 * The claims table is guarded by one monitor held for a few hash lookups (never while generating).
 */
public final class ParallelGen {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.ParallelWorldgenMixin");
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static final LongAdder OFFLOADED = new LongAdder();
    public static final LongAdder FEATURE_JOBS = new LongAdder();
    /** feature jobs that had to wait for a neighbour's window */
    public static final LongAdder WAITED = new LongAdder();

    private static final Object CLAIMS_LOCK = new Object();
    private static final LongOpenHashSet CLAIMED = new LongOpenHashSet();
    private static final ArrayList<FeatureJob> WAITING = new ArrayList<>();

    private ParallelGen() {}

    private static Executor pool() {
        return Util.backgroundExecutor();
    }

    public static boolean shouldOffload(ChunkStep step) {
        if (BYPASS.get()) return false;
        ChunkStatus s = step.targetStatus();
        return s == ChunkStatus.SURFACE || s == ChunkStatus.CARVERS || s == ChunkStatus.FEATURES;
    }

    /** Runs the vanilla step body on the pool; `vanilla` re-enters ChunkMap.applyStep with the bypass flag set. */
    public static CompletableFuture<ChunkAccess> submit(ChunkPos pos, ChunkStep step, Supplier<CompletableFuture<ChunkAccess>> vanilla) {
        OFFLOADED.increment();
        if (step.targetStatus() != ChunkStatus.FEATURES) return CompletableFuture.supplyAsync(() -> run(vanilla), pool());
        FEATURE_JOBS.increment();
        FeatureJob job = new FeatureJob(pos.x, pos.z, vanilla);
        ArrayList<FeatureJob> start;
        synchronized (CLAIMS_LOCK) {
            WAITING.add(job);
            start = startable();
        }
        if (!start.contains(job)) WAITED.increment();
        startAll(start);
        return job.result;
    }

    private static ChunkAccess run(Supplier<CompletableFuture<ChunkAccess>> vanilla) {
        BYPASS.set(true);
        try {
            // these steps return completedFuture: join() only unwraps, it never blocks here
            return vanilla.get().join();
        } finally {
            BYPASS.set(false);
        }
    }

    private static final class FeatureJob implements Runnable {
        final int x, z;
        final Supplier<CompletableFuture<ChunkAccess>> body;
        final CompletableFuture<ChunkAccess> result = new CompletableFuture<>();

        FeatureJob(int x, int z, Supplier<CompletableFuture<ChunkAccess>> body) {
            this.x = x;
            this.z = z;
            this.body = body;
        }

        @Override
        public void run() {
            ChunkAccess c = null;
            Throwable err = null;
            try {
                c = ParallelGen.run(body);
            } catch (Throwable t) {
                err = t;
            }
            ArrayList<FeatureJob> start;
            synchronized (CLAIMS_LOCK) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) CLAIMED.remove(ChunkPos.asLong(x + dx, z + dz));
                start = startable();
            }
            startAll(start);
            // complete after releasing: dependants scheduled by the completion may want this window
            if (err != null) result.completeExceptionally(err);
            else result.complete(c);
        }
    }

    /** Under CLAIMS_LOCK: claims and removes, in FIFO order, every waiter whose window is free and not reserved. */
    private static ArrayList<FeatureJob> startable() {
        ArrayList<FeatureJob> out = new ArrayList<>(2);
        LongOpenHashSet reserved = null;
        for (int i = 0; i < WAITING.size(); ) {
            FeatureJob j = WAITING.get(i);
            if (windowFree(j, CLAIMED) && (reserved == null || windowFree(j, reserved))) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) CLAIMED.add(ChunkPos.asLong(j.x + dx, j.z + dz));
                WAITING.remove(i);
                out.add(j);
            } else {
                if (reserved == null) reserved = new LongOpenHashSet();
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) reserved.add(ChunkPos.asLong(j.x + dx, j.z + dz));
                i++;
            }
        }
        return out;
    }

    private static boolean windowFree(FeatureJob j, LongOpenHashSet set) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if (set.contains(ChunkPos.asLong(j.x + dx, j.z + dz))) return false;
        return true;
    }

    private static void startAll(ArrayList<FeatureJob> jobs) {
        Executor p = pool();
        for (int i = 0; i < jobs.size(); i++) p.execute(jobs.get(i));
    }

    public static String report() {
        int w;
        synchronized (CLAIMS_LOCK) {
            w = WAITING.size();
        }
        return String.format(java.util.Locale.ROOT, "parallel worldgen: offloaded %d steps, %d feature jobs, %d waited for a window, %d still waiting",
                OFFLOADED.sumThenReset(), FEATURE_JOBS.sumThenReset(), WAITED.sumThenReset(), w);
    }
}
