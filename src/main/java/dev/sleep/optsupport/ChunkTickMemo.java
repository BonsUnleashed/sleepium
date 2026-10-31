package dev.sleep.optsupport;

/**
 * exp/chunk-tick-memo (server): ServerChunkCache.tickChunks asks isNaturalSpawningAllowed(pos) for every loaded chunk
 * every tick — a lookup in the entity manager's chunk-visibility hash map (~7 % of the server thread in a normal world
 * with one player, JFR). The answer is remembered on the LevelChunk with an epoch; every write of that map
 * (PersistentEntitySectionManager.updateChunkStatus, rare: loads, unloads, ticket changes) bumps the epoch, so a
 * remembered answer is used only while no visibility changed anywhere since it was read. Exact by construction.
 */
public final class ChunkTickMemo {
    private ChunkTickMemo() {}

    /** bumped on every chunk-visibility change (all levels share it: a change anywhere invalidates everything) */
    public static int epoch = 1;
    public static long hits, misses;

    public interface Holder {
        int sleep$visEpoch();

        boolean sleep$vis();

        void sleep$setVis(int epoch, boolean v);
    }
}
