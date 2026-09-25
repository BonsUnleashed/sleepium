package dev.probe.mixin;

import dev.probe.optsupport.sleep.ChunkTickMemo;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per chunk memo for the fast skip (see SleepProbeFastSkipMixin). Sleep engine only. */
@Mixin(LevelChunk.class)
public abstract class SleepProbeChunkMemoMixin implements ChunkTickMemo {
    @Unique private long probe$memoTick = Long.MIN_VALUE;
    @Unique private boolean probe$memoTicking;

    @Override public long probe$memoTick() { return probe$memoTick; }
    @Override public boolean probe$memoTicking() { return probe$memoTicking; }
    @Override public void probe$setMemo(long tick, boolean ticking) { probe$memoTick = tick; probe$memoTicking = ticking; }
}
