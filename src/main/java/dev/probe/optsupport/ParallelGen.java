package dev.probe.optsupport;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/**
 * exp/parallel-worldgen: run the heavy, synchronous generation steps off the single `worldgen` mailbox actor.
 *
 * <p>Vanilla runs SURFACE, CARVERS and FEATURES inline inside one ProcessorMailbox, so one chunk at a time for the
 * whole level (notes 07). ChunkPyramid declares each step's blockStateWriteRadius:
 * <ul>
 *   <li>SURFACE, CARVERS: 0, they write only their own chunk → run fully in parallel</li>
 *   <li>FEATURES: 1, it writes the 3x3 neighbourhood → lock the 9 chunks' stripes in ascending order (deadlock-free);
 *       jobs whose 3x3 windows don't overlap run in parallel</li>
 * </ul>
 * Everything else (structure starts/references, biomes, noise, light, spawn, full) stays exactly as in vanilla.
 * The pyramid's dependencies already guarantee that a neighbour's FEATURES only writes into chunks that are past
 * CARVERS, so SURFACE/CARVERS never race with a neighbour's writes.
 *
 * <p>Pool: fixed, persistent daemon threads created once ("probe-worldgen-N"), sized -Dlab.opt.worldgenThreads
 * (default cores-2).
 */
public final class ParallelGen {
    public static final boolean ENABLED = Boolean.getBoolean("lab.opt.all") || Boolean.getBoolean("lab.opt.ParallelWorldgenMixin");
    private static final int THREADS = Integer.getInteger("lab.opt.worldgenThreads",
            Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
    private static final int STRIPES = 4096;   // power of two
    private static final ReentrantLock[] LOCKS = new ReentrantLock[STRIPES];
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static final LongAdder OFFLOADED = new LongAdder();
    public static final LongAdder FEATURE_JOBS = new LongAdder();
    public static final LongAdder LOCK_CONTENDED = new LongAdder();

    private static final ExecutorService POOL;

    static {
        for (int i = 0; i < STRIPES; i++) LOCKS[i] = new ReentrantLock();
        AtomicInteger n = new AtomicInteger();
        POOL = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "probe-worldgen-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        if (ENABLED) System.out.println("[probe/opt] parallel worldgen: " + THREADS + " persistent threads");
    }

    private ParallelGen() {}

    public static boolean shouldOffload(ChunkStep step) {
        if (BYPASS.get()) return false;
        ChunkStatus s = step.targetStatus();
        return s == ChunkStatus.SURFACE || s == ChunkStatus.CARVERS || s == ChunkStatus.FEATURES;
    }

    private static int stripe(int cx, int cz) {
        int h = cx * 0x1F1F1F1F ^ cz * 0x61C88647;
        return (h ^ (h >>> 15)) & (STRIPES - 1);
    }

    /** Runs the vanilla step body on the pool; `vanilla` re-enters ChunkMap.applyStep with the bypass flag set. */
    public static CompletableFuture<ChunkAccess> submit(ChunkPos pos, ChunkStep step, Supplier<CompletableFuture<ChunkAccess>> vanilla) {
        OFFLOADED.increment();
        boolean features = step.targetStatus() == ChunkStatus.FEATURES;
        return CompletableFuture.supplyAsync(() -> {
            int[] held = features ? lockWindow(pos) : null;
            BYPASS.set(true);
            try {
                // these steps return completedFuture: join() only unwraps, it never blocks here
                return vanilla.get().join();
            } finally {
                BYPASS.set(false);
                if (held != null) unlock(held);
            }
        }, POOL);
    }

    private static int[] lockWindow(ChunkPos pos) {
        FEATURE_JOBS.increment();
        int[] s = new int[9];
        int k = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) s[k++] = stripe(pos.x + dx, pos.z + dz);
        Arrays.sort(s);
        int n = 0;
        for (int i = 0; i < 9; i++) if (i == 0 || s[i] != s[i - 1]) s[n++] = s[i];   // dedupe (hash collisions)
        int[] held = Arrays.copyOf(s, n);
        for (int stripe : held) {
            ReentrantLock l = LOCKS[stripe];
            if (!l.tryLock()) {
                LOCK_CONTENDED.increment();
                l.lock();
            }
        }
        return held;
    }

    private static void unlock(int[] held) {
        for (int i = held.length - 1; i >= 0; i--) LOCKS[held[i]].unlock();
    }
}
