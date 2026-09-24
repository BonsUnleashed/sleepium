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
                    return true;
                }
                pos = witness;
            } else if (dif < 0) {
                return false;   // full
            } else {
                pos = (long) TAIL.getVolatile(this);
            }
        }
    }

    Object poll() {
        long pos = (long) HEAD.getVolatile(this);
        for (;;) {
            int i = (int) pos & mask;
            long s = (long) SEQ.getAcquire(seq, i);
            long dif = s - (pos + 1);
            if (dif == 0) {
                long witness = (long) HEAD.compareAndExchange(this, pos, pos + 1);
                if (witness == pos) {
                    Object x = items[i];
                    items[i] = null;                       // do not retain the task for GC
                    SEQ.setRelease(seq, i, pos + mask + 1);
                    return x;
                }
                pos = witness;
            } else if (dif < 0) {
                return null;    // empty
            } else {
                pos = (long) HEAD.getVolatile(this);
            }
        }
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
