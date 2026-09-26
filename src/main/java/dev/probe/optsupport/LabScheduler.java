package dev.probe.optsupport;

/**
 * The lab's time queue: "wake key K at game tick T", for any client (scheduled-tick containers today; sleep deadlines
 * and entity sleep next). A binary min-heap over two primitive arrays: no object per entry, arrays grow and are
 * reused, never shrunk. Deletion is lazy: a client that changes a key's time just adds a new entry and ignores stale
 * ones when they come out (it knows the current time of each key). Single-threaded, like the owner it serves.
 */
public final class LabScheduler {
    private long[] time = new long[64], key = new long[64];
    private int size;
    public long added, polled;

    public int size() {
        return size;
    }

    /** Earliest time in the queue, or Long.MAX_VALUE when empty. */
    public long peekTime() {
        return size == 0 ? Long.MAX_VALUE : time[0];
    }

    public void add(long t, long k) {
        if (size == time.length) {
            time = java.util.Arrays.copyOf(time, size * 2);
            key = java.util.Arrays.copyOf(key, size * 2);
        }
        int i = size++;
        while (i > 0) {   // sift up
            int p = (i - 1) >>> 1;
            if (time[p] <= t) break;
            time[i] = time[p];
            key[i] = key[p];
            i = p;
        }
        time[i] = t;
        key[i] = k;
        added++;
    }

    /** Remove the earliest entry and return its key (call only when size() > 0). */
    public long pollKey() {
        long k = key[0];
        int n = --size;
        if (n > 0) {
            long t = time[n], kk = key[n];
            int i = 0;
            while (true) {   // sift down
                int c = 2 * i + 1;
                if (c >= n) break;
                if (c + 1 < n && time[c + 1] < time[c]) c++;
                if (time[c] >= t) break;
                time[i] = time[c];
                key[i] = key[c];
                i = c;
            }
            time[i] = t;
            key[i] = kk;
        }
        polled++;
        return k;
    }

    public void clear() {
        size = 0;
    }
}
