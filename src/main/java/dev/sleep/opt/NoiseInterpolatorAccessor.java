package dev.sleep.opt;

import dev.sleep.optsupport.CellProof;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenSkip (cells): an interpolator's corner slices, for CellProof. */
@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface NoiseInterpolatorAccessor extends CellProof.Corners {
    @Override
    @Accessor("slice0")
    double[][] probe$slice0();

    @Override
    @Accessor("slice1")
    double[][] probe$slice1();
}
