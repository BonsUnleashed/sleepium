package dev.lab.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;

/**
 * One elastic, persistent worker pool per entry point (client, server).
 *
 * <ul>
 *   <li><b>Threads</b>: {@code maxWorkers} are created once in the constructor and never die until {@link #shutdown()}.
 *       "Elastic" means woken on demand, not created: idle workers are parked and cost nothing.</li>
 *   <li><b>Lanes</b>: three preallocated MPMC rings, checked in priority order TICK → (own deque) → CHUNK → BACKGROUND.
 *       Submissions from a worker of this runtime go to its own Chase–Lev deque (LIFO, cache-hot; others steal FIFO).
 *       Rings never block: when full, a task goes to an unbounded overflow queue (counted, should stay ~0).</li>
 *   <li><b>Wake protocol</b>: at most {@code maxSpinners} workers spin for {@code spinNanos} looking for work before
 *       parking. A submitter wakes one parked worker only if nobody is spinning. A worker that finds work while more
 *       remains wakes one more (wake chaining), so the active count follows the backlog: 1 for a trickle, all under load.
 *       Park: set own bit in {@code parkedMask} → re-check for work → park. Wake: CAS-clear a bit → unpark that thread.
 *       No lost wake-ups: both sides use sequentially consistent CAS on the mask around the queue check.</li>
 *   <li><b>OS priority</b>: workers run BELOW_NORMAL, so they never take time from the server/render thread.</li>
 *   <li><b>Tick fan-out</b>: {@link #forRange} splits an index range across the caller and woken workers with a shared
 *       claim counter (chunks), a preallocated job object and a spin-then-park barrier. No allocation per call.</li>
 * </ul>
 */
public final class LabRuntime extends AbstractExecutorService {
    public enum Lane { TICK, CHUNK, BACKGROUND }

    public record Config(String name, int maxWorkers, int maxSpinners, long spinNanos, int osPriority,
                         int laneCapacity, int dequeCapacity, boolean daemon) {
        public static Config of(String name, int maxWorkers) {
            return new Config(name, maxWorkers, 1, 20_000, Integer.getInteger("lab.runtime.priority", OsThreads.BELOW_NORMAL), 1 << 16, 1 << 12, true);
        }
    }

    final Config cfg;
    private final Worker[] workers;
    private final MpmcRing[] lanes = new MpmcRing[3];
    @SuppressWarnings("unchecked")
    private final ConcurrentLinkedQueue<Runnable>[] overflow = new ConcurrentLinkedQueue[3];
    /** bit i set = worker i is parked (or about to park). Accessed only via MASK (CAS / atomic or/and). */
    private volatile long parkedMask;
    private static final java.lang.invoke.VarHandle MASK;

    static {
        try {
            MASK = java.lang.invoke.MethodHandles.lookup().findVarHandle(LabRuntime.class, "parkedMask", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private final AtomicInteger spinners = new AtomicInteger();
    private volatile boolean shutdown;
    private final ThreadLocal<Worker> current = new ThreadLocal<>();

    // metrics (LongAdder: per-thread cells, no shared cache line on the hot path)
    public final LongAdder executed = new LongAdder(), localPushes = new LongAdder(), steals = new LongAdder(),
            parks = new LongAdder(), wakes = new LongAdder(), overflowed = new LongAdder(), failures = new LongAdder();

    public LabRuntime(Config cfg) {
        if (cfg.maxWorkers < 1 || cfg.maxWorkers > 63) throw new IllegalArgumentException("1..63 workers");
        this.cfg = cfg;
        for (int i = 0; i < 3; i++) {
            lanes[i] = new MpmcRing(cfg.laneCapacity);
            overflow[i] = new ConcurrentLinkedQueue<>();
        }
        workers = new Worker[cfg.maxWorkers];
        for (int i = 0; i < workers.length; i++) workers[i] = new Worker(i);
        for (Worker w : workers) w.thread.start();
    }

    public int workerCount() {
        return workers.length;
    }

    public List<Thread> threads() {
        List<Thread> l = new ArrayList<>(workers.length);
        for (Worker w : workers) l.add(w.thread);
        return l;
    }

    /** True if the calling thread is a worker of this runtime. */
    public boolean inWorker() {
        Worker w = current.get();
        return w != null && w.owner == this;
    }

    // ------------------------------------------------------------------ submission

    @Override
    public void execute(Runnable task) {
        execute(task, Lane.CHUNK);
    }

    public void execute(Runnable task, Lane lane) {
        if (task == null) throw new NullPointerException();
        if (shutdown) throw new java.util.concurrent.RejectedExecutionException(cfg.name + " is shut down");
        Worker w = current.get();
        if (w != null && w.owner == this && lane != Lane.TICK) {
            boolean surplus = !w.deque.isEmpty();          // the owner will run the newest task itself (LIFO)
            if (w.deque.push(task)) {
                localPushes.increment();
                if (surplus) signalWork();                 // only extra work justifies waking a sibling
                return;
            }
        }
        if (!lanes[lane.ordinal()].offer(task)) {
            overflow[lane.ordinal()].add(task);
            overflowed.increment();
        }
        signalWork();
    }

    private void signalWork() {
        if (spinners.get() == 0) wakeOne();
    }

    private boolean wakeOne() {
        for (;;) {
            long m = parkedMask;
            if (m == 0) return false;
            long bit = m & -m;
            if (MASK.compareAndSet(this, m, m & ~bit)) {
                wakes.increment();
                LockSupport.unpark(workers[Long.numberOfTrailingZeros(bit)].thread);
                return true;
            }
        }
    }

    boolean hasWork() {
        for (int i = 0; i < 3; i++) {
            if (!lanes[i].isEmpty() || !overflow[i].isEmpty()) return true;
        }
        for (Worker w : workers) {
            if (!w.deque.isEmpty()) return true;
        }
        return tickPending();
    }

    // ------------------------------------------------------------------ workers

    private final class Worker implements Runnable {
        final int id;
        final long bit;
        final WorkDeque deque;
        final Thread thread;
        final LabRuntime owner = LabRuntime.this;
        boolean spinning;
        int stealStart;
        /** Where find() took the last task from: -1 own deque, 0..2 lane, 3 stolen (victim in lastVictim). */
        int lastSource;
        Worker lastVictim;
        boolean lastSourceShared;

        boolean lastSourceNonEmpty() {
            if (lastSource >= 0 && lastSource <= 2) return !lanes[lastSource].isEmpty() || !overflow[lastSource].isEmpty();
            return lastSource == 3 && lastVictim != null && !lastVictim.deque.isEmpty();
        }

        Worker(int id) {
            this.id = id;
            this.bit = 1L << id;
            this.deque = new WorkDeque(cfg.dequeCapacity);
            this.thread = new Thread(this, cfg.name + "-" + id);
            this.thread.setDaemon(cfg.daemon);
            this.stealStart = id + 1;
        }

        @Override
        public void run() {
            current.set(this);
            OsThreads.setCurrentPriority(cfg.osPriority);
            while (!shutdown || hasWork()) {
                if (helpTick()) continue;                    // TICK fan-out has absolute priority
                Runnable t = find();
                if (t != null) {
                    if (spinning) {
                        spinning = false;
                        spinners.decrementAndGet();
                    }
                    // wake chaining: the source we took from still has a backlog -> one more worker (cheap check)
                    if (lastSourceShared && spinners.get() == 0 && parkedMask != 0 && lastSourceNonEmpty()) wakeOne();
                    runTask(t);
                    continue;
                }
                // spin phase: at most cfg.maxSpinners workers spin, the rest go straight to park
                if (!spinning) {
                    if (spinners.incrementAndGet() <= cfg.maxSpinners) spinning = true;
                    else spinners.decrementAndGet();
                }
                if (spinning) {
                    long deadline = System.nanoTime() + cfg.spinNanos;
                    boolean found = false;
                    while (System.nanoTime() < deadline) {
                        if (hasWork()) {
                            found = true;
                            break;
                        }
                        Thread.onSpinWait();
                    }
                    if (found) continue;
                    spinning = false;
                    spinners.decrementAndGet();
                }
                // park phase: publish "parked" -> re-check -> park (no lost wake-ups)
                MASK.getAndBitwiseOr(LabRuntime.this, bit);
                if (hasWork() || shutdown) {
                    clearOwnBit();
                    continue;
                }
                parks.increment();
                LockSupport.park(owner);
                clearOwnBit();   // spurious wake-ups leave the bit set
            }
        }

        private void clearOwnBit() {
            MASK.getAndBitwiseAnd(LabRuntime.this, ~bit);
        }

        private Runnable find() {
            lastSourceShared = true;
            lastSource = 0;
            Object t = lanes[0].poll();
            if (t == null) t = overflow[0].poll();
            if (t == null) {
                t = deque.pop();
                if (t != null) { lastSource = -1; lastSourceShared = false; return (Runnable) t; }
            }
            if (t == null) { lastSource = 1; t = lanes[1].poll(); }
            if (t == null) t = overflow[1].poll();
            if (t == null) { lastSource = 2; t = lanes[2].poll(); }
            if (t == null) t = overflow[2].poll();
            if (t == null && workers.length > 1) {
                lastSource = 3;
                int n = workers.length;
                for (int k = 0; k < n; k++) {
                    Worker v = workers[(stealStart + k) % n];
                    if (v == this) continue;
                    t = v.deque.steal();
                    if (t != null) {
                        steals.increment();
                        stealStart = v.id;   // stay on a productive victim
                        lastVictim = v;
                        break;
                    }
                }
            }
            return (Runnable) t;
        }
    }

    private void runTask(Runnable t) {
        try {
            t.run();
        } catch (Throwable e) {
            failures.increment();
            Thread th = Thread.currentThread();
            Thread.UncaughtExceptionHandler h = th.getUncaughtExceptionHandler();
            if (h != null) h.uncaughtException(th, e);
        } finally {
            executed.increment();
        }
    }

    // ------------------------------------------------------------------ tick fan-out (TICK lane)

    /** The job currently being fanned out (null = none). One immutable object per forRange call. */
    private volatile RangeJob tickJob;

    boolean tickPending() {
        RangeJob j = tickJob;
        return j != null && j.hasUnclaimed();
    }

    private boolean helpTick() {
        RangeJob j = tickJob;
        if (j == null || !j.hasUnclaimed()) return false;
        j.drain();
        return true;
    }

    /**
     * Runs {@code body.accept(i)} for every i in [0, n) on the calling thread plus up to {@code helpers} workers and
     * returns when all indices are done (barrier). Indices are claimed in chunks from one shared counter.
     * One call at a time (the server thread). Exceptions from the body are rethrown after the barrier.
     */
    public void forRange(int n, int chunk, int helpers, IntConsumer body) {
        if (n <= 0) return;
        int c = Math.max(1, chunk);
        RangeJob job = new RangeJob(n, c, body, Thread.currentThread());
        tickJob = job;
        int wake = Math.min(Math.min(helpers, workers.length), (n + c - 1) / c - 1);
        for (int i = 0; i < wake; i++) {
            if (!wakeOne()) break;   // spinners (if any) pick the job up anyway
        }
        job.drain();                 // the caller participates
        job.await(cfg.spinNanos);
        tickJob = null;
        Throwable t = job.error;
        if (t != null) {
            if (t instanceof RuntimeException re) throw re;
            if (t instanceof Error er) throw er;
            throw new RuntimeException(t);
        }
    }

    private static final class RangeJob {
        final int n, chunk;
        final IntConsumer body;
        final Thread waiter;
        final AtomicInteger next = new AtomicInteger();
        final AtomicInteger remaining;
        volatile Throwable error;

        RangeJob(int n, int chunk, IntConsumer body, Thread waiter) {
            this.n = n;
            this.chunk = chunk;
            this.body = body;
            this.waiter = waiter;
            this.remaining = new AtomicInteger(n);
        }

        boolean hasUnclaimed() {
            return next.get() < n;
        }

        void drain() {
            int done = 0;
            for (int s; (s = next.getAndAdd(chunk)) < n; ) {
                int e = Math.min(n, s + chunk);
                try {
                    for (int i = s; i < e; i++) body.accept(i);
                } catch (Throwable t) {
                    error = t;
                }
                done += e - s;
            }
            if (done > 0 && remaining.addAndGet(-done) == 0 && Thread.currentThread() != waiter) {
                LockSupport.unpark(waiter);
            }
        }

        void await(long spinNanos) {
            long deadline = System.nanoTime() + spinNanos;
            while (remaining.get() != 0) {
                if (System.nanoTime() < deadline) Thread.onSpinWait();
                else LockSupport.parkNanos(this, 50_000);
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void shutdown() {
        shutdown = true;
        for (Worker w : workers) LockSupport.unpark(w.thread);
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        List<Runnable> left = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (Object t; (t = lanes[i].poll()) != null; ) left.add((Runnable) t);
            for (Runnable t; (t = overflow[i].poll()) != null; ) left.add(t);
        }
        return left;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        if (!shutdown) return false;
        for (Worker w : workers) if (w.thread.isAlive()) return false;
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (Worker w : workers) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return isTerminated();
            TimeUnit.NANOSECONDS.timedJoin(w.thread, left);
        }
        return isTerminated();
    }

    public String stats() {
        return String.format(java.util.Locale.ROOT,
                "%s: workers %d, executed %d, local %d, steals %d, parks %d, wakes %d, overflow %d, failures %d",
                cfg.name, workers.length, executed.sum(), localPushes.sum(), steals.sum(), parks.sum(), wakes.sum(),
                overflowed.sum(), failures.sum());
    }
}
