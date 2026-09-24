package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;

/**
 * One elastic, persistent worker pool per entry point (client, server).
 *
 * <ul>
 *   <li><b>Threads</b>: {@code maxWorkers} {@link LabThread}s are created once in the constructor and live until
 *       {@link #shutdown()}. "Elastic" = woken on demand, never created on demand. Parked workers cost nothing.</li>
 *   <li><b>Lanes, sharded</b>: each priority lane (TICK, CHUNK, BACKGROUND) has {@code shards} preallocated MPMC rings.
 *       An external submitter always uses the shard picked by its thread id, so different producers do not fight
 *       over one head/tail cache line. A worker scans shards starting at its own index. When a ring is full the task
 *       goes to a per-lane overflow queue (counted; should stay ~0).</li>
 *   <li><b>Local deques</b>: a task submitted by a worker of this runtime goes to that worker's Chase–Lev deque
 *       (LIFO, cache-hot); idle workers steal FIFO. The current worker is found through the {@link LabThread} field,
 *       not a ThreadLocal lookup.</li>
 *   <li><b>Wake protocol</b>: at most {@code maxSpinners} workers spin for {@code spinNanos} before parking. A submitter
 *       wakes one parked worker only if nobody spins. A worker that takes a task from a shared source that still has a
 *       backlog wakes one more (wake chaining), so the active count follows the load. Park: set own bit → re-check →
 *       park. Wake: CAS-clear a bit → unpark. No lost wake-ups (sequentially consistent CAS around the queue check).</li>
 *   <li><b>OS priority</b>: BELOW_NORMAL by default ({@code -Dlab.runtime.priority}), so the pool yields to the server
 *       and render threads.</li>
 *   <li><b>Tick fan-out</b>: {@link #forRange} runs an index range on the caller plus woken workers with a shared claim
 *       counter and a spin-then-park barrier.</li>
 * </ul>
 */
public final class LabRuntime extends AbstractExecutorService {
    public enum Lane { TICK, CHUNK, BACKGROUND }

    public record Config(String name, int maxWorkers, int maxSpinners, long spinNanos, int osPriority,
                         int laneCapacity, int dequeCapacity, boolean daemon) {
        public static Config of(String name, int maxWorkers) {
            return new Config(name, maxWorkers, 1, 20_000, Integer.getInteger("lab.runtime.priority", OsThreads.BELOW_NORMAL),
                    1 << 14, 1 << 12, true);
        }
    }

    /** Worker thread carrying its own state: "thread-local by construction", no ThreadLocal lookups. */
    public static final class LabThread extends Thread {
        final LabRuntime owner;
        final Worker worker;

        LabThread(LabRuntime owner, Worker worker, String name) {
            super(worker, name);
            this.owner = owner;
            this.worker = worker;
        }
    }

    private static final VarHandle MASK;

    static {
        try {
            MASK = MethodHandles.lookup().findVarHandle(LabRuntime.class, "parkedMask", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final Config cfg;
    private final Worker[] workers;
    private final int shardMask;
    /** rings[lane * shards + shard] */
    private final MpmcRing[] rings;
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final ConcurrentLinkedQueue<Runnable>[] overflow = new ConcurrentLinkedQueue[3];
    /** bit i set = worker i parked (or about to park). */
    private volatile long parkedMask;
    private final AtomicInteger spinners = new AtomicInteger();
    private volatile boolean shutdown;
    private volatile RangeJob tickJob;
    /** set by TICK-lane submitters; lets workers skip scanning the (usually empty) TICK rings */
    private volatile boolean tickLaneHint;

    public final LongAdder executed = new LongAdder(), localPushes = new LongAdder(), steals = new LongAdder(),
            parks = new LongAdder(), wakes = new LongAdder(), overflowed = new LongAdder(), failures = new LongAdder();

    public LabRuntime(Config cfg) {
        if (cfg.maxWorkers < 1 || cfg.maxWorkers > 63) throw new IllegalArgumentException("1..63 workers");
        this.cfg = cfg;
        int shards = Integer.highestOneBit(Math.max(1, cfg.maxWorkers) * 2 - 1);   // next power of two >= workers
        this.shardMask = shards - 1;
        this.rings = new MpmcRing[3 * shards];
        for (int i = 0; i < rings.length; i++) rings[i] = new MpmcRing(cfg.laneCapacity);
        for (int i = 0; i < 3; i++) overflow[i] = new ConcurrentLinkedQueue<>();
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

    public boolean inWorker() {
        return Thread.currentThread() instanceof LabThread t && t.owner == this;
    }

    // ------------------------------------------------------------------ submission

    @Override
    public void execute(Runnable task) {
        execute(task, Lane.CHUNK);
    }

    public void execute(Runnable task, Lane lane) {
        if (task == null) throw new NullPointerException();
        Thread ct = Thread.currentThread();
        if (ct instanceof LabThread lt && lt.owner == this && lane != Lane.TICK && pushLocal(lt.worker, task)) return;
        int shard = (int) ct.threadId() & shardMask;
        if (!rings[lane.ordinal() * (shardMask + 1) + shard].offer(task)) submitSlow(task, lane);
        if (lane == Lane.TICK && !tickLaneHint) tickLaneHint = true;
        if (spinners.get() == 0) wakeOne();
    }

    private boolean pushLocal(Worker w, Runnable task) {
        boolean surplus = !w.deque.isEmpty();   // the owner runs the newest task itself next (LIFO)
        if (!w.deque.push(task)) return false;
        localPushes.increment();
        if (surplus && spinners.get() == 0) wakeOne();
        return true;
    }

    private void submitSlow(Runnable task, Lane lane) {
        if (shutdown) throw new RejectedExecutionException(cfg.name + " is shut down");
        overflow[lane.ordinal()].add(task);
        overflowed.increment();
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
        for (MpmcRing r : rings) if (!r.isEmpty()) return true;
        for (int i = 0; i < 3; i++) if (!overflow[i].isEmpty()) return true;
        for (Worker w : workers) if (!w.deque.isEmpty()) return true;
        RangeJob j = tickJob;
        return j != null && j.hasUnclaimed();
    }

    // ------------------------------------------------------------------ workers

    final class Worker implements Runnable {
        final int id;
        final long bit;
        final WorkDeque deque;
        final LabThread thread;
        boolean spinning;
        int stealStart;
        /** true if the last task came from a shared source (ring/overflow/steal) that may still hold more */
        boolean lastShared;
        Object lastSource;

        Worker(int id) {
            this.id = id;
            this.bit = 1L << id;
            this.deque = new WorkDeque(cfg.dequeCapacity);
            this.thread = new LabThread(LabRuntime.this, this, cfg.name + "-" + id);
            this.thread.setDaemon(cfg.daemon);
            this.stealStart = id + 1;
        }

        @Override
        public void run() {
            OsThreads.setCurrentPriority(cfg.osPriority);
            while (!shutdown || hasWork()) {
                RangeJob j = tickJob;
                if (j != null && j.hasUnclaimed()) {       // TICK fan-out has absolute priority
                    j.drain();
                    continue;
                }
                Runnable t = find();
                if (t != null) {
                    if (spinning) {
                        spinning = false;
                        spinners.decrementAndGet();
                    }
                    if (lastShared && parkedMask != 0 && spinners.get() == 0 && sourceHasMore()) wakeOne();
                    runTask(t);
                    continue;
                }
                idle();
            }
        }

        private boolean sourceHasMore() {
            Object s = lastSource;
            if (s instanceof MpmcRing r) return !r.isEmpty();
            if (s instanceof WorkDeque d) return !d.isEmpty();
            if (s instanceof ConcurrentLinkedQueue<?> q) return !q.isEmpty();
            return false;
        }

        /** Priority order: TICK lane, own deque, CHUNK lane, BACKGROUND lane, then steal. */
        private Runnable find() {
            Object t = null;
            if (tickLaneHint) {
                t = pollLane(0);
                if (t != null) return (Runnable) t;
                tickLaneHint = false;                        // drained (a racing submitter sets it again)
                t = pollLane(0);                             // re-check after clearing: no lost TICK task
                if (t != null) return (Runnable) t;
            }
            if (!deque.isEmpty()) {                          // pop() has a full fence: skip it when empty
                t = deque.pop();
                if (t != null) {
                    lastShared = false;
                    return (Runnable) t;
                }
            }
            t = pollLane(1);
            if (t == null) t = pollLane(2);
            if (t == null) t = steal();
            return (Runnable) t;
        }

        private Object pollLane(int lane) {
            int n = shardMask + 1, base = lane * n;
            for (int k = 0; k < n; k++) {
                MpmcRing r = rings[base + ((id + k) & shardMask)];
                Object t = r.poll();
                if (t != null) {
                    lastShared = true;
                    lastSource = r;
                    return t;
                }
            }
            Object t = overflow[lane].poll();
            if (t != null) {
                lastShared = true;
                lastSource = overflow[lane];
            }
            return t;
        }

        private Object steal() {
            int n = workers.length;
            if (n < 2) return null;
            for (int k = 0; k < n; k++) {
                Worker v = workers[(stealStart + k) % n];
                if (v == this) continue;
                Object t = v.deque.steal();
                if (t != null) {
                    steals.increment();
                    stealStart = v.id;
                    lastShared = true;
                    lastSource = v.deque;
                    return t;
                }
            }
            return null;
        }

        private void idle() {
            if (!spinning) {
                if (spinners.incrementAndGet() <= cfg.maxSpinners) spinning = true;
                else spinners.decrementAndGet();
            }
            if (spinning) {
                long deadline = System.nanoTime() + cfg.spinNanos;
                while (System.nanoTime() < deadline) {
                    if (hasWork()) return;
                    Thread.onSpinWait();
                }
                spinning = false;
                spinners.decrementAndGet();
            }
            MASK.getAndBitwiseOr(LabRuntime.this, bit);   // publish "parked"
            if (hasWork() || shutdown) {                   // re-check: a submitter may have missed our bit
                MASK.getAndBitwiseAnd(LabRuntime.this, ~bit);
                return;
            }
            parks.increment();
            LockSupport.park(LabRuntime.this);
            MASK.getAndBitwiseAnd(LabRuntime.this, ~bit);  // spurious wake-ups leave the bit set
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

    /**
     * Runs {@code body.accept(i)} for every i in [0, n) on the calling thread plus up to {@code helpers} workers and
     * returns when all indices are done (barrier). One call at a time (the server thread). Exceptions from the body
     * are rethrown on the caller after the barrier.
     */
    public void forRange(int n, int chunk, int helpers, IntConsumer body) {
        if (n <= 0) return;
        int c = Math.max(1, chunk);
        RangeJob job = new RangeJob(n, c, body, Thread.currentThread());
        tickJob = job;
        int wake = Math.min(Math.min(helpers, workers.length), (n + c - 1) / c - 1);
        for (int i = 0; i < wake; i++) if (!wakeOne()) break;
        job.drain();
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
            if (done > 0 && remaining.addAndGet(-done) == 0 && Thread.currentThread() != waiter) LockSupport.unpark(waiter);
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
        for (MpmcRing r : rings) for (Object t; (t = r.poll()) != null; ) left.add((Runnable) t);
        for (int i = 0; i < 3; i++) for (Runnable t; (t = overflow[i].poll()) != null; ) left.add(t);
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
                "%s: workers %d, shards %d, executed %d, local %d, steals %d, parks %d, wakes %d, overflow %d, failures %d",
                cfg.name, workers.length, shardMask + 1, executed.sum(), localPushes.sum(), steals.sum(), parks.sum(),
                wakes.sum(), overflowed.sum(), failures.sum());
    }
}
