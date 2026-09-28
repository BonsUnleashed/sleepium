package dev.probe.opt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.probe.optsupport.GenScout;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (lab.genscout): NoiseChunk.computePreliminarySurfaceLevel (one column scan) calls and time. */
@Mixin(NoiseChunk.class)
public abstract class GenScoutSurfaceMixin {
    @WrapMethod(method = "computePreliminarySurfaceLevel")
    private int probe$time(long packedChunkPos, Operation<Integer> original) {
        long t = System.nanoTime();
        try {
            return original.call(packedChunkPos);
        } finally {
            GenScout.SURF_NANOS.add(System.nanoTime() - t);
            GenScout.SURF_CALLS.increment();
        }
    }
}
