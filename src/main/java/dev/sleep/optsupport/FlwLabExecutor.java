package dev.sleep.optsupport;

import dev.engine_room.flywheel.impl.task.TaskExecutorImpl;
import dev.lab.runtime.LabRuntime;
import dev.lab.runtime.MpmcRing;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Flywheel's task executor on the frame's workers (FlwExecutorMixin, {@code -Dlab.opt.FlwExecutorMixin=true}). Flywheel
 * starts its own pool of up to 10 threads sharing one ConcurrentLinkedDeque, each spinning on it 10 us before waiting on
 * a monitor (measured on a Create factory world: ~40 % of those threads' time in the deque's operations, 0.57 cores).
 * Here its plans run on FrameReactor's pool - the workers that already render entities and do the client light - so
 * the client has one set of frame workers, not two competing ones.
 * <p>Semantics kept: execute() may be called from any thread (plans fan out from workers); the render thread's
 * syncPoint / syncUntil / syncWhile run queued tasks themselves (oldest first; Flywheel's took the newest) and otherwise
 * wait - parked, woken by the task that ends the work or brings a new task - until every task submitted so far is done.
 */
public final class FlwLabExecutor implements TaskExecutorImpl {
    private final LabRuntime pool;
    private final MpmcRing queue = new MpmcRing(1 << 13);   // preallocated; when full the submitter runs the task itself
    private final AtomicInteger pending = new AtomicInteger();   // submitted and not finished
    private volatile Thread waiter;
    private int errors = 10;
    private final Runnable worker = this::runOne;   // one object for every submission

    public FlwLabExecutor(LabRuntime pool) {
        this.pool = pool;
    }

    @Override
    public int threadCount() {
        return pool.workerCount();
    }

    @Override
    public void execute(Runnable task) {
        pending.incrementAndGet();
        if (!queue.offer(task)) {   // full: run it here (an Executor may run a task on the caller)
            run(task);
            return;
        }
        pool.execute(worker);   // a worker takes one task; the render thread may have taken it first
        Thread w = waiter;
        if (w != null) LockSupport.unpark(w);   // a waiting syncPoint can run it itself
    }

    private boolean runOne() {
        Runnable task = (Runnable) queue.poll();
        if (task == null) return false;
        run(task);
        return true;
    }

    private void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            if (errors > 0) {
                errors--;
                System.out.println("[sleep/flywheel] task failed: " + e);
                e.printStackTrace();
            }
        } finally {
            if (pending.decrementAndGet() == 0) {
                Thread w = waiter;
                if (w != null) LockSupport.unpark(w);
            }
        }
    }

    /** One step of a wait: run a queued task, or park until work arrives or ends; true when nothing is pending. */
    private boolean syncOne() {
        if (runOne()) return false;
        if (pending.get() == 0) return true;
        waiter = Thread.currentThread();   // published first, then checked again: a task offered meanwhile is seen or unparks us
        try {
            if (runOne()) return false;
            if (pending.get() != 0) LockSupport.parkNanos(200_000L);
        } finally {
            waiter = null;
        }
        return pending.get() == 0;
    }

    @Override
    public boolean syncUntil(BooleanSupplier cond) {
        while (!cond.getAsBoolean()) if (syncOne()) return cond.getAsBoolean();
        return true;
    }

    @Override
    public boolean syncWhile(BooleanSupplier cond) {
        while (cond.getAsBoolean()) if (syncOne()) return !cond.getAsBoolean();
        return true;
    }

    @Override
    public void syncPoint() {
        while (!syncOne()) {
        }
    }
}
