package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntConsumer;

/**
 * LabRuntime-lite: the smallest lock-free persistent pool, kept to compare with {@link LabRuntime}.
 *
 * <ul>
 *   <li>Fixed threads, created once, parked when idle.</li>
 *   <li>ONE shared queue ({@link SegmentQueue}: unbounded, lock-free, XADD to enqueue, one CAS to take). No lanes,
 *       rings, local deques or stealing: any worker takes any task, so a task that blocks on a subtask it submitted
 *       can never strand it.</li>
 *   <li>Wake: a submitter wakes one parked worker if nobody spins; a worker that took a task while more are queued
 *       wakes one more (chain). ≤ 1 spinner for {@code spinNanos}. Park: set bit → re-check → park.</li>
 * </ul>
 */
public final class LiteRuntime extends AbstractExecutorService {
    private static final VarHandle MASK;

    static {
        try {
            MASK = MethodHandles.lookup().findVarHandle(LiteRuntime.class, "parkedMask", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String name;
    private final long spinNanos;
    private final Thread[] threads;
    private final SegmentQueue queue = new SegmentQueue();
    private final AtomicInteger spinners = new AtomicInteger();
    private volatile long parkedMask;
    private volatile boolean shutdown;
    public final LongAdder parks = new LongAdder(), wakes = new LongAdder();

    public LiteRuntime(String name, int workers) {
        this(name, workers, 20_000, OsThreads.BELOW_NORMAL, true, null);
    }

    public LiteRuntime(String name, int workers, long spinNanos, int osPriority, boolean daemon, Thread.UncaughtExceptionHandler handler) {
        if (workers < 1 || workers > 63) throw new IllegalArgumentException("1..63 workers");
        this.name = name;
        this.spinNanos = spinNanos;
        threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            long bit = 1L << i;
            Thread t = new Thread(() -> {
                OsThreads.setCurrentPriority(osPriority);
                loop(bit);
            }, name + "-" + (i + 1));
            t.setDaemon(daemon);
            if (handler != null) t.setUncaughtExceptionHandler(handler);
            threads[i] = t;
        }
        for (Thread t : threads) t.start();
    }

    public List<Thread> threads() {
        return List.of(threads);
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) throw new NullPointerException();
        if (shutdown) throw new RejectedExecutionException(name + " is shut down");
        queue.offer(task);
        if (spinners.get() == 0) wakeOne();
    }

    private void wakeOne() {
        for (;;) {
            long m = parkedMask;
            if (m == 0) return;
            long bit = m & -m;
            if (MASK.compareAndSet(this, m, m & ~bit)) {
                wakes.increment();
                LockSupport.unpark(threads[Long.numberOfTrailingZeros(bit)]);
                return;
            }
        }
    }

    private void loop(long bit) {
        boolean spinning = false;
        while (!shutdown || !queue.isEmpty()) {
            Object t = queue.poll();
            if (t != null) {
                if (spinning) {
                    spinning = false;
                    spinners.decrementAndGet();
                }
                if (parkedMask != 0 && spinners.get() == 0 && !queue.isEmpty()) wakeOne();   // chain
                run((Runnable) t);
                continue;
            }
            // idle: at most one spinner, then park with the arm -> re-check -> park protocol
            if (!spinning) {
                if (spinners.incrementAndGet() == 1) spinning = true;
                else spinners.decrementAndGet();
            }
            if (spinning) {
                long deadline = System.nanoTime() + spinNanos;
                boolean found = false;
                while (System.nanoTime() < deadline) {
                    if (!queue.isEmpty()) { found = true; break; }
                    Thread.onSpinWait();
                }
                if (found) continue;
                spinning = false;
                spinners.decrementAndGet();
            }
            MASK.getAndBitwiseOr(this, bit);
            if (!queue.isEmpty() || shutdown) {
                MASK.getAndBitwiseAnd(this, ~bit);
                continue;
            }
            parks.increment();
            LockSupport.park(this);
            MASK.getAndBitwiseAnd(this, ~bit);
        }
    }

    private static void run(Runnable t) {
        try {
            t.run();
        } catch (Throwable e) {
            Thread th = Thread.currentThread();
            Thread.UncaughtExceptionHandler h = th.getUncaughtExceptionHandler();
            if (h != null) h.uncaughtException(th, e);
        }
    }

    /** Barrier fan-out: indices [0, n) in chunks, run by the caller plus up to {@code helpers} workers. */
    public void forRange(int n, int chunk, int helpers, IntConsumer body) {
        if (n <= 0) return;
        int c = Math.max(1, chunk), chunks = (n + c - 1) / c;
        AtomicInteger next = new AtomicInteger(), remaining = new AtomicInteger(chunks);
        Thread caller = Thread.currentThread();
        Throwable[] error = new Throwable[1];
        Runnable drain = () -> {
            for (int k; (k = next.getAndIncrement()) < chunks; ) {
                try {
                    for (int i = k * c, e = Math.min(n, i + c); i < e; i++) body.accept(i);
                } catch (Throwable ex) {
                    synchronized (error) { if (error[0] == null) error[0] = ex; }
                }
                if (remaining.decrementAndGet() == 0) LockSupport.unpark(caller);
            }
        };
        for (int h = Math.min(helpers, chunks - 1); h > 0; h--) execute(drain);
        drain.run();
        long spinEnd = System.nanoTime() + 50_000;
        while (remaining.get() != 0) {
            if (System.nanoTime() < spinEnd) Thread.onSpinWait();
            else LockSupport.parkNanos(this, 50_000);
        }
        Throwable e = error[0];
        if (e instanceof RuntimeException re) throw re;
        if (e instanceof Error er) throw er;
        if (e != null) throw new RuntimeException(e);
    }

    @Override
    public void shutdown() {
        shutdown = true;
        for (Thread t : threads) LockSupport.unpark(t);
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        List<Runnable> left = new ArrayList<>();
        for (Object t; (t = queue.poll()) != null; ) left.add((Runnable) t);
        return left;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        if (!shutdown) return false;
        for (Thread t : threads) if (t.isAlive()) return false;
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        for (Thread t : threads) {
            long left = deadline - System.nanoTime();
            if (left <= 0) return isTerminated();
            TimeUnit.NANOSECONDS.timedJoin(t, left);
        }
        return isTerminated();
    }
}
