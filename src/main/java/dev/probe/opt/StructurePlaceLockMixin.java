package dev.probe.opt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/parallel-worldgen: one StructureStart is shared by every chunk its pieces cover, and pieces keep mutable state
 * across chunks (MineShaftCorridor.hasPlacedSpider, ScatteredFeaturePiece's heightPosition and bounding box moved by
 * updateAverageGroundHeight, the temples' placed-chest flags, modded pieces alike). Vanilla places one chunk at a
 * time; with FEATURES of chunks 3+ apart running at once, placements of one start are serialised on the start
 * itself. Lock order: the chunk window's locks, then this one; nothing is taken while holding it → no cycle.
 */
@Mixin(StructureStart.class)
public abstract class StructurePlaceLockMixin {
    @WrapMethod(method = "placeInChunk")
    private void probe$serialise(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                 BoundingBox box, ChunkPos chunkPos, Operation<Void> original) {
        synchronized (this) {
            original.call(level, structureManager, generator, random, box, chunkPos);
        }
    }
}
