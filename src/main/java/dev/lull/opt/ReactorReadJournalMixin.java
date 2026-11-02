package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.optsupport.ReadJournal;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** ServerReactor (chunk loads): inside ChunkSerializer.read on the pool, shared-state calls go to the ReadJournal. */
@Mixin(ChunkSerializer.class)
public abstract class ReactorReadJournalMixin {
    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;checkConsistencyWithBlocks(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/LevelChunkSection;)V"))
    private static void sleep$poi(PoiManager poi, SectionPos pos, LevelChunkSection section, Operation<Void> original) {
        if (!ReadJournal.defer(() -> original.call(poi, pos, section))) original.call(poi, pos, section);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;retainData(Lnet/minecraft/world/level/ChunkPos;Z)V"))
    private static void sleep$retain(LevelLightEngine light, ChunkPos pos, boolean retain, Operation<Void> original) {
        if (!ReadJournal.defer(() -> original.call(light, pos, retain))) original.call(light, pos, retain);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"))
    private static void sleep$light(LevelLightEngine light, LightLayer layer, SectionPos pos, DataLayer data, Operation<Void> original) {
        if (!ReadJournal.defer(() -> original.call(light, layer, pos, data))) original.call(light, layer, pos, data);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;reportMisplacedChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;)V"))
    private static void sleep$misplaced(MinecraftServer server, ChunkPos pos, ChunkPos expected, RegionStorageInfo info, Operation<Void> original) {
        if (!ReadJournal.defer(() -> original.call(server, pos, expected, info))) original.call(server, pos, expected, info);
    }

    @WrapOperation(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event sleep$post(IEventBus bus, Event event, Operation<Event> original) {
        if (ReadJournal.defer(() -> original.call(bus, event))) return event;
        return original.call(bus, event);
    }
}
