package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.GenProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): deserializing a saved chunk (ChunkSerializer.read, on the main thread). */
@Mixin(ChunkSerializer.class)
public abstract class GenProfileReadMixin {
    @WrapMethod(method = "read")
    private static ProtoChunk sleep$read(ServerLevel level, PoiManager poi, RegionStorageInfo info, ChunkPos pos, CompoundTag tag,
                                         Operation<ProtoChunk> original) {
        if (!GenProfile.active()) return original.call(level, poi, info, pos, tag);
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            return original.call(level, poi, info, pos, tag);
        } finally {
            GenProfile.add(Thread.currentThread().getName().startsWith("Server thread") ? "main: ChunkSerializer.read" : "actor: ChunkSerializer.read (pool)", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
