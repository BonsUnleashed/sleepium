package dev.lull.opt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenReactor: the concentric-ring position futures getRingPositionsFor joins (filled at startup, then read-only). */
@Mixin(ChunkGeneratorStructureState.class)
public interface ReactorRingsAccessor {
    @Accessor("ringPositions")
    Map<ConcentricRingsStructurePlacement, CompletableFuture<List<ChunkPos>>> sleep$rings();
}
