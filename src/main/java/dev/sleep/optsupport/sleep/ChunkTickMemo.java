package dev.sleep.optsupport.sleep;

/** Added to LevelChunk: "is this chunk block-ticking with entities loaded" memoized per game tick. */
public interface ChunkTickMemo {
    long probe$memoTick();

    boolean probe$memoTicking();

    void probe$setMemo(long tick, boolean ticking);
}
