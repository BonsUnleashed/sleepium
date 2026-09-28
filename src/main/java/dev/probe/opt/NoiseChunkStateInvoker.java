package dev.probe.opt;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** WorldgenSkip (cells): NoiseChunk.getInterpolatedState, cellWidth, cellHeight are protected; WorldgenSkipFillMixin's doFill calls it. */
@Mixin(NoiseChunk.class)
public interface NoiseChunkStateInvoker {
    @Invoker("getInterpolatedState")
    BlockState probe$getInterpolatedState();

    @Invoker("cellWidth")
    int probe$cellWidth();

    @Invoker("cellHeight")
    int probe$cellHeight();
}
