package dev.sleep.opt;

import dev.sleep.optsupport.ChunkTickMemo;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** ChunkTickMemoMixin: the remembered answer and its epoch on the chunk. */
@Mixin(LevelChunk.class)
public abstract class ChunkTickMemoChunkMixin implements ChunkTickMemo.Holder {
    @Unique private int sleep$visEpoch;
    @Unique private boolean sleep$vis;

    @Override public int sleep$visEpoch() { return sleep$visEpoch; }
    @Override public boolean sleep$vis() { return sleep$vis; }
    @Override public void sleep$setVis(int epoch, boolean v) { sleep$visEpoch = epoch; sleep$vis = v; }
}
