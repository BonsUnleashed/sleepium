package dev.lull.opt;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** WorldgenSkip (cells): NoiseChunk.getInterpolatedState, cellWidth, cellHeight are protected; WorldgenSkipFillMixin's doFill calls it. */
@Mixin(NoiseChunk.class)
public interface NoiseChunkStateInvoker {
    @Invoker("getInterpolatedState")
    BlockState sleep$getInterpolatedState();

    @Invoker("cellWidth")
    int sleep$cellWidth();

    @Invoker("cellHeight")
    int sleep$cellHeight();
}
