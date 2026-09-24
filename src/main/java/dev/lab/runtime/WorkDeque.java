package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Chase–Lev work-stealing deque on a fixed circular array (no resizing, no allocation).
 *
 * <p>The owner pushes/pops at the bottom (LIFO: the most recent task, hot in cache); thieves steal from the top
 * (FIFO: the oldest task) with one CAS. push() returns false when full; the owner then uses the shared lane instead.
 *
 * <p>Memory ordering (JMM translation of the C11 version by Lê et al., "Correct and Efficient Work-Stealing"):
 * push: plain slot write, release store of bottom. pop: volatile store of bottom (StoreLoad fence before reading
 * top), CAS on top only for the last element. steal: acquire top, acquire bottom, read slot, CAS top.
 */
final class WorkDeque {
    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle TOP, BOTTOM;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            TOP = l.findVarHandle(WorkDeque.class, "top", long.class);
            BOTTOM = l.findVarHandle(WorkDeque.class, "bottom", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Object[] a;
    private final int mask;
    @SuppressWarnings("unused") private long p00, p01, p02, p03, p04, p05, p06, p07;
    private volatile long top;      // thieves
    @SuppressWarnings("unused") private long p10, p11, p12, p13, p14, p15, p16, p17;
    private volatile long bottom;   // owner
    @SuppressWarnings("unused") private long p20, p21, p22, p23, p24, p25, p26, p27;

    WorkDeque(int capacityPow2) {
        if (Integer.bitCount(capacityPow2) != 1) throw new IllegalArgumentException("capacity must be a power of two");
        a = new Object[capacityPow2];
        mask = capacityPow2 - 1;
    }

    /** Owner only. */
    boolean push(Object x) {
        long b = (long) BOTTOM.getOpaque(this);
        long t = (long) TOP.getAcquire(this);
        if (b - t > mask) return false;   // full
        SLOT.setOpaque(a, (int) b & mask, x);
        BOTTOM.setRelease(this, b + 1);
        return true;
    }

    /** Owner only. */
    Object pop() {
        long b = (long) BOTTOM.getOpaque(this) - 1;
        BOTTOM.setVolatile(this, b);           // full fence: must be visible before we read top
        long t = (long) TOP.getVolatile(this);
        if (t <= b) {
            Object x = SLOT.getOpaque(a, (int) b & mask);
            if (t == b) {
                // last element: race against thieves for it
                if (!TOP.compareAndSet(this, t, t + 1)) x = null;
                BOTTOM.setRelease(this, b + 1);
            } else {
                SLOT.setOpaque(a, (int) b & mask, null);
            }
            return x;
        }
        BOTTOM.setRelease(this, b + 1);         // was empty
        return null;
    }

    /** Any thread. Returns null when empty or when it lost a race (caller simply tries elsewhere). */
    Object steal() {
        long t = (long) TOP.getAcquire(this);
        long b = (long) BOTTOM.getAcquire(this);
        if (t < b) {
            Object x = SLOT.getAcquire(a, (int) t & mask);
            if (TOP.compareAndSet(this, t, t + 1)) return x;
        }
        return null;
    }

    boolean isEmpty() {
        return (long) TOP.getAcquire(this) >= (long) BOTTOM.getAcquire(this);
    }
}
