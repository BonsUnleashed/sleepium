package dev.lull.api;

import dev.lab.runtime.MpmcRing;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A completion ring: any thread offers results, the owner thread handles them. One drain task is posted to the
 * owner's executor per batch (not one per item), so a burst of thousands of results costs the owner one queue entry;
 * items keep the order they were offered in per producer. Nothing blocks: a full ring spills into an unbounded queue
 * that the same drain empties.
 *
 * <pre>{@code
 * // server side: results of pool work applied on the server thread
 * CompletionRing<Result> done = new CompletionRing<>(4096, server, r -> apply(r));
 * Pools.background().execute(() -> done.offer(compute(snapshot)));
 *
 * // client side: the owner is the Minecraft instance (the render thread)
 * CompletionRing<Mesh> uploads = new CompletionRing<>(1024, Minecraft.getInstance(), m -> upload(m));
 * }</pre>
 *
 * The server and the Minecraft client are both {@link Executor}s whose {@code execute} runs the task on their own
 * thread and, for the server, wakes it if it sleeps between ticks. {@link #drainNow()} lets the owner empty the ring at
 * a point of its own choosing as well (for example at the end of its tick).
 */
public final class CompletionRing<T> {
    private final MpmcRing ring;
    private final ConcurrentLinkedQueue<T> spill = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Executor owner;
    private final Consumer<? super T> handler;
    private final Runnable drainTask = this::drainNow;

    /**
     * @param capacity ring slots, rounded up to a power of two (overflow beyond it spills, it is not lost)
     * @param owner    the executor of the thread that must handle the results
     * @param handler  runs on the owner thread for every result
     */
    public CompletionRing(int capacity, Executor owner, Consumer<? super T> handler) {
        this.ring = new MpmcRing(Integer.highestOneBit(Math.max(2, capacity) * 2 - 1));
        this.owner = owner;
        this.handler = handler;
    }

    /** any thread: hand one result to the owner */
    public void offer(T item) {
        // while the spill holds items everyone appends there, so a producer's items never overtake each other
        if (!spill.isEmpty() || !ring.offer(item)) spill.add(item);
        if (scheduled.compareAndSet(false, true)) owner.execute(drainTask);
    }

    /** owner thread: handle everything offered so far; returns how many */
    @SuppressWarnings("unchecked")
    public int drainNow() {
        scheduled.set(false);   // first: an offer from here on posts a new drain
        int n = 0;
        for (Object o; (o = ring.poll()) != null; n++) handler.accept((T) o);
        for (T t; (t = spill.poll()) != null; n++) handler.accept(t);
        return n;
    }
}
