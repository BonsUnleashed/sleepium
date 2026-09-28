package dev.probe.opt;

import dev.probe.optsupport.ChunkTickMemo;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** ChunkTickMemoMixin: the remembered answer and its epoch on the chunk. */
@Mixin(LevelChunk.class)
public abstract class ChunkTickMemoChunkMixin implements ChunkTickMemo.Holder {
    @Unique private int probe$visEpoch;
    @Unique private boolean probe$vis;

    @Override public int probe$visEpoch() { return probe$visEpoch; }
    @Override public boolean probe$vis() { return probe$vis; }
    @Override public void probe$setVis(int epoch, boolean v) { probe$visEpoch = epoch; probe$vis = v; }
}
