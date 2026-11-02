package dev.lull.mixin;

import dev.lull.optsupport.sleep.ChunkTickMemo;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per chunk memo for the fast skip (see SleepProbeFastSkipMixin). Sleep engine only. */
@Mixin(LevelChunk.class)
public abstract class SleepProbeChunkMemoMixin implements ChunkTickMemo {
    @Unique private long sleep$memoTick = Long.MIN_VALUE;
    @Unique private boolean sleep$memoTicking;

    @Override public long sleep$memoTick() { return sleep$memoTick; }
    @Override public boolean sleep$memoTicking() { return sleep$memoTicking; }
    @Override public void sleep$setMemo(long tick, boolean ticking) { sleep$memoTick = tick; sleep$memoTicking = ticking; }
}
