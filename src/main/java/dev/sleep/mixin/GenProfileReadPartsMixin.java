package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sleep.GenProfile;
import java.util.Map;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Measurement only (genbench): the parts of ChunkSerializer.read on the main thread. */
@Mixin(ChunkSerializer.class)
public abstract class GenProfileReadPartsMixin {
    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"))
    private static void probe$poi(PoiManager poi, SectionPos pos, LevelChunkSection section, Operation<Void> original) {
        if (!GenProfile.active()) { original.call(poi, pos, section); return; }
        long t = System.nanoTime(), m = GenProfile.allocated();
        original.call(poi, pos, section);
        GenProfile.add("main: (inside read) POI consistency", System.nanoTime() - t, GenProfile.allocated() - m);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"))
    private static void probe$light(LevelLightEngine light, LightLayer layer, SectionPos pos, DataLayer data, Operation<Void> original) {
        if (!GenProfile.active()) { original.call(light, layer, pos, data); return; }
        long t = System.nanoTime(), m = GenProfile.allocated();
        original.call(light, layer, pos, data);
        GenProfile.add("main: (inside read) queue light data", System.nanoTime() - t, GenProfile.allocated() - m);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;unpackStructureStart(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;J)Ljava/util/Map;"))
    private static Map<Structure, StructureStart> probe$starts(StructurePieceSerializationContext ctx, CompoundTag tag, long seed,
                                                              Operation<Map<Structure, StructureStart>> original) {
        if (!GenProfile.active()) return original.call(ctx, tag, seed);
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            return original.call(ctx, tag, seed);
        } finally {
            GenProfile.add("main: (inside read) structure starts", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;unpackStructureReferences(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Map;"))
    private static Map<?, ?> probe$refs(net.minecraft.core.RegistryAccess access, ChunkPos pos, CompoundTag tag, Operation<Map<?, ?>> original) {
        if (!GenProfile.active()) return original.call(access, pos, tag);
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            return original.call(access, pos, tag);
        } finally {
            GenProfile.add("main: (inside read) structure references", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
