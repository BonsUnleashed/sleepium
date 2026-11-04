package dev.lull.optsupport;

/** Struct-of-arrays records: kind, state, pos, block, neighbourPos, direction (-1 = null), flags, limit, moved, idx. */
public final class NeighborRecs {
    public byte[] kind = new byte[16];
    public Object[] state = new Object[16], pos = new Object[16], block = new Object[16], npos = new Object[16];
    public int[] dir = new int[16], flags = new int[16], limit = new int[16], idx = new int[16];
    public boolean[] moved = new boolean[16];
    public int size;

    public int add() {
        if (size == kind.length) grow();
        return size++;
    }

    void grow() {
        int n = kind.length * 2;
        kind = java.util.Arrays.copyOf(kind, n);
        state = java.util.Arrays.copyOf(state, n);
        pos = java.util.Arrays.copyOf(pos, n);
        block = java.util.Arrays.copyOf(block, n);
        npos = java.util.Arrays.copyOf(npos, n);
        dir = java.util.Arrays.copyOf(dir, n);
        flags = java.util.Arrays.copyOf(flags, n);
        limit = java.util.Arrays.copyOf(limit, n);
        idx = java.util.Arrays.copyOf(idx, n);
        moved = java.util.Arrays.copyOf(moved, n);
    }

    public void copyTo(int i, NeighborRecs to) {
        int j = to.add();
        to.kind[j] = kind[i];
        to.state[j] = state[i];
        to.pos[j] = pos[i];
        to.block[j] = block[i];
        to.npos[j] = npos[i];
        to.dir[j] = dir[i];
        to.flags[j] = flags[i];
        to.limit[j] = limit[i];
        to.idx[j] = idx[i];
        to.moved[j] = moved[i];
    }

    public void release(int i) {   // no stale references kept alive
        state[i] = pos[i] = block[i] = npos[i] = null;
    }

    public void clear() {
        for (int i = 0; i < size; i++) release(i);
        size = 0;
    }
}
