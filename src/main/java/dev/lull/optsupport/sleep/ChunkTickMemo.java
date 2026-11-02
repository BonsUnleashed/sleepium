package dev.lull.optsupport.sleep;

/** Added to LevelChunk: "is this chunk block-ticking with entities loaded" memoized per game tick. */
public interface ChunkTickMemo {
    long sleep$memoTick();

    boolean sleep$memoTicking();

    void sleep$setMemo(long tick, boolean ticking);
}
