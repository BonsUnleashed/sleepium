package dev.lab.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Unbounded lock-free MPMC queue made of ONE-SHOT array segments: the overflow path behind the bounded lane rings.
 *
 * <p>Producers claim a slot with a single {@code getAndIncrement} (XADD, no CAS retry loop) on the tail segment; a
 * segment whose counter ran past its length is full forever, and the producer moves on to (or links) the next one.
 * A slot is published by a release store of the element: non-null means ready. Consumers claim up to {@code max}
 * consecutive ready slots with ONE CAS on the segment's head, exactly like the ring's batch claim.
 *
 * <p>Slots are never reused, so there is no ABA and no "closed" race: an exhausted segment simply becomes garbage.
 * Memory is allocated only while the queue is in use (overload), about 4 bytes per task with compressed oops, versus
 * a 24-byte node per task for ConcurrentLinkedQueue.
 */
final class SegmentQueue {
    static final int SEG = 4096;

    private static final VarHandle HEAD, TAIL, S_HEAD, S_CLAIM, S_NEXT;
    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Object[].class);

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(SegmentQueue.class, "head", Segment.class);
            TAIL = l.findVarHandle(SegmentQueue.class, "tail", Segment.class);
            S_HEAD = l.findVarHandle(Segment.class, "head", int.class);
            S_CLAIM = l.findVarHandle(Segment.class, "claim", int.class);
            S_NEXT = l.findVarHandle(Segment.class, "next", Segment.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static final class Segment {
        final Object[] slots = new Object[SEG];
        @SuppressWarnings("unused") long p00, p01, p02, p03, p04, p05, p06, p07;
        volatile int claim;          // producers: next slot to claim (may run past SEG)
        @SuppressWarnings("unused") long p10, p11, p12, p13, p14, p15, p16, p17;
        volatile int head;           // consumers: next slot to take
        volatile Segment next;
    }

    @SuppressWarnings("unused") private long p00, p01, p02, p03, p04, p05, p06, p07;
    private volatile Segment head;
    @SuppressWarnings("unused") private long p10, p11, p12, p13, p14, p15, p16, p17;
    private volatile Segment tail;
    @SuppressWarnings("unused") private long p20, p21, p22, p23, p24, p25, p26, p27;

    SegmentQueue() {
        Segment s = new Segment();
        head = s;
        tail = s;
    }

    void offer(Object x) {
        if (x == null) throw new NullPointerException();
        for (;;) {
            Segment t = tail;
            int i = (int) S_CLAIM.getAndAdd(t, 1);
            if (i < SEG) {
                SLOT.setRelease(t.slots, i, x);
                return;
            }
            // full forever: link (or help link) the next segment and swing tail
            Segment n = t.next;
            if (n == null) {
                Segment fresh = new Segment();
                n = S_NEXT.compareAndSet(t, null, fresh) ? fresh : t.next;
            }
            TAIL.compareAndSet(this, t, n);
        }
    }

    /** Takes up to max ready elements with one CAS; 0 if empty (or the oldest claimed slot is still being written). */
    int pollBatch(Object[] out, int max) {
        for (;;) {
            Segment h = head;
            int pos = h.head;
            if (pos >= SEG) {                            // exhausted: advance to the successor if there is one
                Segment n = h.next;
                if (n == null) return 0;
                HEAD.compareAndSet(this, h, n);
                continue;
            }
            int n = 0, lim = Math.min(max, SEG - pos);
            while (n < lim && SLOT.getAcquire(h.slots, pos + n) != null) n++;
            if (n == 0) return 0;                        // empty, or a producer claimed pos but has not written yet
            if (S_HEAD.compareAndSet(h, pos, pos + n)) {
                for (int k = 0; k < n; k++) {
                    out[k] = h.slots[pos + k];
                    h.slots[pos + k] = null;             // help GC if the segment lingers
                }
                return n;
            }
        }
    }

    /** Takes one element (one CAS), no allocation; null if empty or the oldest claimed slot is not written yet. */
    Object poll() {
        for (;;) {
            Segment h = head;
            int pos = h.head;
            if (pos >= SEG) {
                Segment n = h.next;
                if (n == null) return null;
                HEAD.compareAndSet(this, h, n);
                continue;
            }
            Object x = SLOT.getAcquire(h.slots, pos);
            if (x == null) return null;
            if (S_HEAD.compareAndSet(h, pos, pos + 1)) {
                h.slots[pos] = null;
                return x;
            }
        }
    }

    /** Racy hint: true if nothing was ever claimed past the consumers' position. */
    boolean isEmpty() {
        Segment h = head;
        int pos = h.head;
        if (pos < SEG) return pos >= Math.min(h.claim, SEG);
        return h.next == null || isEmptyFrom(h.next);
    }

    private static boolean isEmptyFrom(Segment s) {
        return s.head >= Math.min(s.claim, SEG) && s.next == null;
    }
}
