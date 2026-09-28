package dev.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.probe.GenProfile;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): the NOISE and BIOMES work on Worker-Main; NoiseChunk creation is inside one of them. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class GenProfileNoiseMixin {
    @WrapMethod(method = "doFill")
    private ChunkAccess probe$fill(Blender blender, StructureManager sm, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY,
                                   Operation<ChunkAccess> original) {
        if (!GenProfile.active()) return original.call(blender, sm, random, chunk, minCellY, cellCountY);
        long t = System.nanoTime(), m = GenProfile.allocated(), pn = GenProfile.perlin();
        try {
            return original.call(blender, sm, random, chunk, minCellY, cellCountY);
        } finally {
            GenProfile.add("noise.doFill", System.nanoTime() - t, GenProfile.allocated() - m, GenProfile.perlin() - pn);
        }
    }

    @WrapMethod(method = "doCreateBiomes")
    private void probe$biomes(Blender blender, RandomState random, StructureManager sm, ChunkAccess chunk, Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call(blender, random, sm, chunk);
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated(), pn = GenProfile.perlin();
        try {
            original.call(blender, random, sm, chunk);
        } finally {
            GenProfile.add("biomes.doCreateBiomes", System.nanoTime() - t, GenProfile.allocated() - m, GenProfile.perlin() - pn);
        }
    }

    @WrapMethod(method = "createNoiseChunk")
    private NoiseChunk probe$noiseChunk(ChunkAccess chunk, StructureManager sm, Blender blender, RandomState random, Operation<NoiseChunk> original) {
        if (!GenProfile.active()) return original.call(chunk, sm, blender, random);
        long t = System.nanoTime(), m = GenProfile.allocated(), pn = GenProfile.perlin();
        try {
            return original.call(chunk, sm, blender, random);
        } finally {
            GenProfile.add("(inside) createNoiseChunk", System.nanoTime() - t, GenProfile.allocated() - m, GenProfile.perlin() - pn);
        }
    }
}
