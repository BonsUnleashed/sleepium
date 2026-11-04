package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.optsupport.ReadJournal;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ServerReactor (chunk loads): inside ChunkSerializer.read on the pool, shared-state calls go to the ReadJournal.
 * 1.20.1: no misplaced-chunk report (a wrong position is only logged); the ChunkDataEvent.Load posts (both chunk types)
 * are Forge's IEventBus.post; Forge capabilities are read on the main thread (ReactorChunkLoadMixin).
 */
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
            target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean sleep$post(IEventBus bus, Event event, Operation<Boolean> original) {
        // the post's result (cancelled) is not read by ChunkSerializer.read
        if (ReadJournal.defer(() -> original.call(bus, event))) return false;
        return original.call(bus, event);
    }
}
