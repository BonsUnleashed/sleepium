package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench), inside noise.doFill: the cell-corner density evaluation (fillSlice: the density tree at
 * 5 x 49 x 5 corners) and the per-cell caches (selectCellYZ: cacheAllInCell = final density + Beardifier for every
 * block of the cell). doFill minus these = per-block work (interpolation, aquifer, ore veins, block writes).
 */
@Mixin(NoiseChunk.class)
public abstract class GenProfileNoiseChunkMixin {
    @WrapMethod(method = "fillSlice")
    private void probe$corners(boolean isSlice0, int start, Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call(isSlice0, start);
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call(isSlice0, start);
        } finally {
            GenProfile.add("(inside) noise.corners", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapMethod(method = "selectCellYZ")
    private void probe$cell(int y, int z, Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call(y, z);
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call(y, z);
        } finally {
            GenProfile.add("(inside) noise.cellCaches", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapMethod(method = "wrap")
    private net.minecraft.world.level.levelgen.DensityFunction probe$wrap(net.minecraft.world.level.levelgen.DensityFunction f,
                                                                            Operation<net.minecraft.world.level.levelgen.DensityFunction> original) {
        if (!GenProfile.active()) return original.call(f);
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            return original.call(f);
        } finally {
            GenProfile.add("(inside) NoiseChunk.wrap", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
