package dev.sleep.optsupport;

/** Added to Level by ChunkCacheMixin: drop the one-chunk lookup cache (called when vanilla clears its own). */
public interface ChunkCacheHolder {
    void probe$ccReset();
}
