package dev.probe.optsupport;

/**
 * The lab's time queue: "wake key K at game tick T", for any client (scheduled-tick containers, sleep deadlines, next
 * entity sleep). A timing wheel: a ring of 1024 one-tick slots for the near future (O(1) add and take) plus a small
 * min-heap for later times, moved into the ring as the window reaches them. Slots and heap are primitive arrays,
 * grown and reused, never shrunk; no object per entry.
 *
 * <p>It returns, for each tick, the keys that are due, in no particular order (clients that need an order, like the
 * scheduled ticks, sort afterwards as vanilla does). Deletion is lazy: a client that changes a key's time just adds a
 * new entry and ignores stale ones (it knows each key's current time). A time already past is due at the next take.
 * Single-threaded, like the owner it serves.
 */
public final class LabScheduler {
    public static final long NONE = Long.MIN_VALUE;
    private static final int SLOTS = 1024, MASK = SLOTS - 1;

    private final long[][] slot = new long[SLOTS][];
    private final int[] slotSize = new int[SLOTS];
    private long cursor = Long.MIN_VALUE;   // next tick to drain; MIN_VALUE until the first take
    private int inRing;

    // overflow: min-heap of (time, key)
    private long[] hTime = new long[16], hKey = new long[16];
    private int hSize;

    public long added, taken;

    public int size() {
        return inRing + hSize;
    }

    public void add(long t, long k) {
        added++;
        if (cursor == Long.MIN_VALUE || t >= cursor + SLOTS) {
            heapAdd(t, k);
            return;
        }
        ringAdd(t < cursor ? cursor : t, k);
    }

    /** The next key due at or before {@code now}, or NONE. Call until NONE once per tick. */
    public long takeDue(long now) {
        if (cursor == Long.MIN_VALUE) cursor = hSize > 0 ? Math.min(now, hTime[0]) : now;
        while (cursor <= now) {
            refill();
            int s = (int) (cursor & MASK);
            int n = slotSize[s];
            if (n > 0) {
                slotSize[s] = n - 1;
                inRing--;
                taken++;
                return slot[s][n - 1];
            }
            if (cursor == now) break;   // keep the cursor on now: entries added for now are still taken this tick
            cursor++;
        }
        return NONE;
    }

    /** Close the tick: later adds for times up to {@code now} become due at the next tick. */
    public void endTick(long now) {
        if (cursor != Long.MIN_VALUE && cursor <= now) cursor = now + 1;
    }

    private void refill() {
        long limit = cursor + SLOTS;
        while (hSize > 0 && hTime[0] < limit) {
            long t = hTime[0], k = hKey[0];
            heapPoll();
            ringAdd(t < cursor ? cursor : t, k);
        }
    }

    private void ringAdd(long t, long k) {
        int s = (int) (t & MASK);
        long[] a = slot[s];
        int n = slotSize[s];
        if (a == null) slot[s] = a = new long[8];
        else if (n == a.length) slot[s] = a = java.util.Arrays.copyOf(a, n * 2);
        a[n] = k;
        slotSize[s] = n + 1;
        inRing++;
    }

    private void heapAdd(long t, long k) {
        if (hSize == hTime.length) {
            hTime = java.util.Arrays.copyOf(hTime, hSize * 2);
            hKey = java.util.Arrays.copyOf(hKey, hSize * 2);
        }
        int i = hSize++;
        while (i > 0) {
            int p = (i - 1) >>> 1;
            if (hTime[p] <= t) break;
            hTime[i] = hTime[p];
            hKey[i] = hKey[p];
            i = p;
        }
        hTime[i] = t;
        hKey[i] = k;
    }

    private void heapPoll() {
        int n = --hSize;
        if (n == 0) return;
        long t = hTime[n], kk = hKey[n];
        int i = 0;
        while (true) {
            int c = 2 * i + 1;
            if (c >= n) break;
            if (c + 1 < n && hTime[c + 1] < hTime[c]) c++;
            if (hTime[c] >= t) break;
            hTime[i] = hTime[c];
            hKey[i] = hKey[c];
            i = c;
        }
        hTime[i] = t;
        hKey[i] = kk;
    }
}
