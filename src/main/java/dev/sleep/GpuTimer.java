package dev.sleep;

import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * -Dsleep.drawStats (client): GPU time of a frame from two timestamp queries (start and end of GameRenderer.render).
 * A ring of 64 frames; every 32 frames the half written 32..63 frames ago is read (long finished), so reading results
 * costs one driver round trip per 32 frames and does not change what is measured.
 */
public final class GpuTimer {
    private static final int RING = 64, HALF = RING / 2;
    private static final int[] BEGIN = new int[RING], END = new int[RING];
    private static int slot;
    private static long written, frames, gpuNanos;
    /** per frame slot: timestamps at renderLevel's section changes (DrawStats.section) and the sections' names */
    private static final int MAXS = 40;
    private static final int[][] SQ = new int[RING][];
    private static final String[][] SN = new String[RING][MAXS];
    private static final int[] SC = new int[RING];
    private static final java.util.Map<String, long[]> GPU_SECTIONS = new java.util.LinkedHashMap<>();

    /** a timestamp where renderLevel enters section {@code name} (null: renderLevel ended) */
    static void section(String name) {
        if (!DrawStats.active || BEGIN[0] == 0) return;
        int c = SC[slot];
        if (c >= MAXS) return;
        if (SQ[slot] == null) {
            SQ[slot] = new int[MAXS];
            GL15C.glGenQueries(SQ[slot]);
        }
        GL33C.glQueryCounter(SQ[slot][c], GL33C.GL_TIMESTAMP);
        SN[slot][c] = name == null ? "(after renderLevel)" : name;
        SC[slot] = c + 1;
    }

    private GpuTimer() {}

    static void reset() {
        frames = 0;
        gpuNanos = 0;
        GPU_SECTIONS.clear();
        java.util.Arrays.fill(SC, 0);
        written = 0;
        slot = 0;
    }

    public static void begin() {
        if (!DrawStats.active) return;
        if (BEGIN[0] == 0) {
            GL15C.glGenQueries(BEGIN);
            GL15C.glGenQueries(END);
        }
        GL33C.glQueryCounter(BEGIN[slot], GL33C.GL_TIMESTAMP);
        SC[slot] = 0;
    }

    public static void end() {
        if (!DrawStats.active || BEGIN[0] == 0) return;
        GL33C.glQueryCounter(END[slot], GL33C.GL_TIMESTAMP);
        written++;
        slot = (slot + 1) % RING;
        if (slot % HALF == 0 && written >= RING) {
            // the half we are about to overwrite next was written 32..63 frames ago
            int from = slot;
            for (int i = from; i < from + HALF; i++) {
                long b = GL33C.glGetQueryObjecti64(BEGIN[i], GL15C.GL_QUERY_RESULT), e = GL33C.glGetQueryObjecti64(END[i], GL15C.GL_QUERY_RESULT);
                gpuNanos += e - b;
                frames++;
                int n = SC[i];   // the sections: from each timestamp to the next (the last to the frame's end)
                long prev = b;
                String prevName = "(before renderLevel)";
                for (int k = 0; k < n; k++) {
                    long t = GL33C.glGetQueryObjecti64(SQ[i][k], GL15C.GL_QUERY_RESULT);
                    GPU_SECTIONS.computeIfAbsent(prevName, x -> new long[1])[0] += t - prev;
                    prev = t;
                    prevName = SN[i][k];
                }
                GPU_SECTIONS.computeIfAbsent(prevName, x -> new long[1])[0] += e - prev;
            }
        }
    }

    static String report() {
        if (frames == 0) return "gpu time: no samples";
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "gpu time (render start -> end on the GPU clock): %.3f ms per frame (%d frames)",
                gpuNanos / 1e6 / frames, frames));
        sb.append("\n  gpu time by renderLevel section (ms per frame):");
        GPU_SECTIONS.forEach((k, t) -> sb.append(String.format(java.util.Locale.ROOT, " %s %.3f;", k, t[0] / 1e6 / frames)));
        return sb.toString();
    }
}
