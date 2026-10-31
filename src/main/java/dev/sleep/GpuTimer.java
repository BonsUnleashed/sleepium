package dev.sleep;

import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * -Dprobe.drawStats (client): GPU time of a frame from two timestamp queries (start and end of GameRenderer.render).
 * A ring of 64 frames; every 32 frames the half written 32..63 frames ago is read (long finished), so reading results
 * costs one driver round trip per 32 frames and does not change what is measured.
 */
public final class GpuTimer {
    private static final int RING = 64, HALF = RING / 2;
    private static final int[] BEGIN = new int[RING], END = new int[RING];
    private static int slot;
    private static long written, frames, gpuNanos;

    private GpuTimer() {}

    static void reset() {
        frames = 0;
        gpuNanos = 0;
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
            }
        }
    }

    static String report() {
        return frames == 0 ? "gpu time: no samples" : String.format(java.util.Locale.ROOT, "gpu time (render start -> end on the GPU clock): %.3f ms per frame (%d frames)",
                gpuNanos / 1e6 / frames, frames);
    }
}
