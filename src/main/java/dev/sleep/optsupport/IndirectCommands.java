package dev.sleep.optsupport;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL44C;

/**
 * Draw commands for glMultiDrawElementsIndirect, in an immutable buffer (glBufferStorage) written with glBufferSubData
 * as a ring: each upload at the next offset, wrapping to 0 when the rest is too short, re-created twice as large when
 * one upload does not fit. Intel's driver (UHD, 32.0.101) refuses every indirect draw from a buffer whose store was
 * specified a second time with glBufferData (GL_INVALID_OPERATION, nothing drawn), and the commands were re-specified
 * before every draw (10-29, N100: entities not drawn, terrain sections lost); immutable storage it accepts. Without
 * ARB_buffer_storage the store is re-specified as before. Render thread only.
 */
public final class IndirectCommands {
    private static Boolean immutable;
    private int buffer;
    private long capacity, cursor;

    /** Uploads the commands at address, leaves the buffer bound to GL_DRAW_INDIRECT_BUFFER, returns their offset. */
    public long upload(long address, long bytes) {
        if (buffer == 0) buffer = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, buffer);
        if (bytes <= 0) return 0;
        if (!immutable()) {
            GL15C.nglBufferData(GL40C.GL_DRAW_INDIRECT_BUFFER, bytes, address, GL15C.GL_STREAM_DRAW);
            return 0;
        }
        if (bytes > capacity) {
            GL15C.glDeleteBuffers(buffer);
            buffer = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, buffer);
            capacity = Math.max(64 << 10, Long.highestOneBit(bytes - 1) << 2);
            GL44C.glBufferStorage(GL40C.GL_DRAW_INDIRECT_BUFFER, capacity, GL44C.GL_DYNAMIC_STORAGE_BIT);
            cursor = 0;
        }
        if (cursor + bytes > capacity) cursor = 0;
        long at = cursor;
        GL15C.nglBufferSubData(GL40C.GL_DRAW_INDIRECT_BUFFER, at, bytes, address);
        cursor = (at + bytes + 15) & ~15L;
        return at;
    }

    private static boolean immutable() {
        Boolean i = immutable;
        if (i == null) {
            var caps = GL.getCapabilities();
            immutable = i = (caps.OpenGL44 || caps.GL_ARB_buffer_storage) && !Boolean.getBoolean("lab.indirect.respecify");
            System.out.println("[probe/opt] indirect commands: " + (i ? "immutable buffer, written in place" : "re-specified per upload"));
        }
        return i;
    }
}
