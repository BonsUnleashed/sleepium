package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Bounded multi-producer multi-consumer ring (Dmitry Vyukov's algorithm), fully preallocated.
 *
 * <p>Each slot carries a sequence number. A producer claims position p when seq[p] == p and publishes with
 * seq[p] = p+1; a consumer takes p when seq[p] == p+1 and frees it with seq[p] = p+capacity. One CAS per operation,
 * no allocation, no locks. offer() returns false when full (the caller decides on the overflow path).
 */
final class MpmcRing {
    private static final VarHandle SEQ = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle HEAD, TAIL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(MpmcRing.class, "head", long.class);
            TAIL = l.findVarHandle(MpmcRing.class, "tail", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final int mask;
    private final Object[] items;
    private final long[] seq;

    // head (consumers) and tail (producers) are written by different threads: keep them on separate cache lines
    @SuppressWarnings("unused") private long p00, p01, p02, p03, p04, p05, p06, p07;
    private volatile long head;
    @SuppressWarnings("unused") private long p10, p11, p12, p13, p14, p15, p16, p17;
    private volatile long tail;
    @SuppressWarnings("unused") private long p20, p21, p22, p23, p24, p25, p26, p27;

    MpmcRing(int capacityPow2) {
        if (Integer.bitCount(capacityPow2) != 1) throw new IllegalArgumentException("capacity must be a power of two");
        this.mask = capacityPow2 - 1;
        this.items = new Object[capacityPow2];
        this.seq = new long[capacityPow2];
        for (int i = 0; i < capacityPow2; i++) seq[i] = i;
    }

    boolean offer(Object x) {
        return offerPos(x) >= 0;
    }

    /** Like offer, returns the claimed position or -1 when full. */
    long offerPos(Object x) {
        long pos = (long) TAIL.getVolatile(this);
        for (;;) {
            int i = (int) pos & mask;
            long s = (long) SEQ.getAcquire(seq, i);
            long dif = s - pos;
            if (dif == 0) {
                long witness = (long) TAIL.compareAndExchange(this, pos, pos + 1);
                if (witness == pos) {
                    items[i] = x;
                    SEQ.setRelease(seq, i, pos + 1);
                    return pos;
                }
                pos = witness;
            } else if (dif < 0) {
                return -1;      // full
            } else {
                pos = (long) TAIL.getVolatile(this);
            }
        }
    }

    /**
     * Offer for a ring that is known to have exactly ONE producer thread (e.g. a per-submitter ring): no CAS, the tail
     * is a plain field of the owner published with a release store. Consumers still use poll/pollBatch.
     */
    boolean offerOwned(Object x) {
        long pos = (long) TAIL.getOpaque(this);
        int i = (int) pos & mask;
        if ((long) SEQ.getAcquire(seq, i) != pos) return false;   // full
        items[i] = x;
        SEQ.setRelease(seq, i, pos + 1);
        TAIL.setRelease(this, pos + 1);
        return true;
    }

    /** Fast path kept small so C2 inlines it into the worker loop; the retry loop lives in pollSlow(). */
    Object poll() {
        long pos = (long) HEAD.getVolatile(this);
        int i = (int) pos & mask;
        long s = (long) SEQ.getAcquire(seq, i);
        if (s != pos + 1) return s < pos + 1 ? null : pollSlow();
        if (!HEAD.compareAndSet(this, pos, pos + 1)) return pollSlow();
        Object x = items[i];
        items[i] = null;
        SEQ.setRelease(seq, i, pos + mask + 1);
        return x;
    }

    private Object pollSlow() {
        long pos = (long) HEAD.getVolatile(this);
        for (;;) {
            int i = (int) pos & mask;
            long s = (long) SEQ.getAcquire(seq, i);
            long dif = s - (pos + 1);
            if (dif == 0) {
                long witness = (long) HEAD.compareAndExchange(this, pos, pos + 1);
                if (witness == pos) {
                    Object x = items[i];
                    items[i] = null;
                    SEQ.setRelease(seq, i, pos + mask + 1);
                    return x;
                }
                pos = witness;
            } else if (dif < 0) {
                return null;
            } else {
                pos = (long) HEAD.getVolatile(this);
            }
        }
    }

    /**
     * Batch claim: take up to {@code max} consecutive published slots with ONE CAS on head.
     * Returns the number of items written to {@code out}. Consumers contend max-times less on the head line.
     */
    int pollBatch(Object[] out, int max) {
        for (;;) {
            long pos = (long) HEAD.getVolatile(this);
            int n = 0;
            while (n < max) {
                long s = (long) SEQ.getAcquire(seq, (int) (pos + n) & mask);
                if (s != pos + n + 1) break;
                n++;
            }
            if (n == 0) {
                long s = (long) SEQ.getAcquire(seq, (int) pos & mask);
                if (s < pos + 1) return 0;   // empty
                continue;                     // stale head: another consumer moved it
            }
            if (HEAD.compareAndSet(this, pos, pos + n)) {
                for (int k = 0; k < n; k++) {
                    int i = (int) (pos + k) & mask;
                    out[k] = items[i];
                    items[i] = null;
                    SEQ.setRelease(seq, i, pos + k + mask + 1);
                }
                return n;
            }
        }
    }

    /**
     * True if the item right before {@code pos} is still in the ring (published, not yet freed by a consumer). Used by
     * a submitter to skip the wake-up: whoever published that older item already woke (or saw) a worker, and a worker
     * never parks while items are visible (it re-checks after publishing its parked bit). Reads the neighbouring
     * sequence slot, which is on the cache line the producer just wrote 7 times out of 8 (ForkJoinPool's trick),
     * instead of the consumers' head line.
     */
    boolean hasOlderThan(long pos) {
        return (long) SEQ.getVolatile(seq, (int) (pos - 1) & mask) == pos;
    }

    /** Racy hint, exact enough for "is there work?" decisions (callers re-check). */
    boolean isEmpty() {
        return head >= tail;
    }

    int sizeEstimate() {
        long s = tail - head;
        return s <= 0 ? 0 : (int) Math.min(s, Integer.MAX_VALUE);
    }
}
