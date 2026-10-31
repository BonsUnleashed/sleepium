package dev.sleep.optsupport;

/** Implemented on ChunkMap by ReactorChunkLoadMixin: apply the chunk-type marks of chunks created off the main thread. */
public interface ChunkLoadMarks {
    void probe$drainMarks();
}
