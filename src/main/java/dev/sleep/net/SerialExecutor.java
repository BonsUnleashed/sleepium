package dev.sleep.net;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tasks run one at a time, in submission order, on a shared pool (a mailbox): no thread of its own, nothing held while
 * idle. One drain task on the pool runs everything queued, so a burst costs one pool task.
 */
public final class SerialExecutor implements Executor {
    private final Executor pool;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Runnable drain = this::drain;

    public SerialExecutor(Executor pool) {
        this.pool = pool;
    }

    @Override
    public void execute(Runnable r) {
        queue.add(r);
        if (scheduled.compareAndSet(false, true)) pool.execute(drain);
    }

    private void drain() {
        for (Runnable r; (r = queue.poll()) != null; ) {
            try {
                r.run();
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("sleep").error("serial task failed", e);
            }
        }
        scheduled.set(false);
        // an item added between the last poll and the reset would wait for the next execute: take it now
        if (!queue.isEmpty() && scheduled.compareAndSet(false, true)) pool.execute(drain);
    }
}
