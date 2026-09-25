package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Single-producer single-consumer ring with CACHED indices and batched publication (FastFlow / Disruptor style).
 *
 * <p>The producer owns {@code tail}, the consumer owns {@code head}; neither is ever CASed. Each side keeps a private
 * copy of the other side's index and re-reads the shared one only when the ring looks full (producer) or empty
 * (consumer), so in steady state the two cache lines are not bounced on every element. The producer can also defer
 * publishing its tail ({@link #offerDeferred} + {@link #publish}) so a whole batch costs one release store.
 */
final class SpscRing {
    private static final VarHandle HEAD, TAIL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(SpscRing.class, "head", long.class);
            TAIL = l.findVarHandle(SpscRing.class, "tail", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Object[] a;
    private final int mask;
    @SuppressWarnings("unused") private long p00, p01, p02, p03, p04, p05, p06, p07;
    private volatile long head;          // consumer-owned, published
    private long cachedTail;             // consumer-private copy of tail
    @SuppressWarnings("unused") private long p10, p11, p12, p13, p14, p15, p16, p17;
    private volatile long tail;          // producer-owned, published
    private long localTail;              // producer-private, not yet published
    private long cachedHead;             // producer-private copy of head
    @SuppressWarnings("unused") private long p20, p21, p22, p23, p24, p25, p26, p27;

    SpscRing(int capacityPow2) {
        if (Integer.bitCount(capacityPow2) != 1) throw new IllegalArgumentException("capacity must be a power of two");
        a = new Object[capacityPow2];
        mask = capacityPow2 - 1;
    }

    /** Producer: write without publishing. Returns false when full. */
    boolean offerDeferred(Object x) {
        long t = localTail;
        if (t - cachedHead > mask) {
            cachedHead = (long) HEAD.getAcquire(this);
            if (t - cachedHead > mask) return false;
        }
        a[(int) t & mask] = x;
        localTail = t + 1;
        return true;
    }

    /** Producer: make all deferred writes visible with one release store. */
    void publish() {
        TAIL.setRelease(this, localTail);
    }

    boolean offer(Object x) {
        if (!offerDeferred(x)) return false;
        publish();
        return true;
    }

    /** Consumer: take one item (head published per item). */
    Object poll() {
        long h = (long) HEAD.getOpaque(this);
        if (h >= cachedTail) {
            cachedTail = (long) TAIL.getAcquire(this);
            if (h >= cachedTail) return null;
        }
        int i = (int) h & mask;
        Object x = a[i];
        a[i] = null;
        HEAD.setRelease(this, h + 1);
        return x;
    }

    /** Consumer: drain up to max items; head is published once per batch. */
    int pollBatch(Object[] out, int max) {
        long h = (long) HEAD.getOpaque(this);
        if (h >= cachedTail) {
            cachedTail = (long) TAIL.getAcquire(this);
            if (h >= cachedTail) return 0;
        }
        int n = (int) Math.min(max, cachedTail - h);
        for (int k = 0; k < n; k++) {
            int i = (int) (h + k) & mask;
            out[k] = a[i];
            a[i] = null;
        }
        HEAD.setRelease(this, h + n);
        return n;
    }

    /** Racy hint (any thread). */
    boolean isEmpty() {
        return head >= tail;
    }
}
