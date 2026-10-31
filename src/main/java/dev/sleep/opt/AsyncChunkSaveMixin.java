package dev.sleep.opt;

import dev.sleep.optsupport.AsyncChunkSave;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** exp/async-chunk-save: see AsyncChunkSave (ChunkMap.save: the tag with placeholders, then the deferred store). */
@Mixin(ChunkMap.class)
public abstract class AsyncChunkSaveMixin {
    @Redirect(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;write(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag probe$write(ServerLevel level, ChunkAccess chunk) {
        return AsyncChunkSave.write((ChunkStorage) (Object) this, level, chunk);
    }

    @Redirect(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> probe$store(ChunkMap self, ChunkPos pos, CompoundTag tag) {
        return AsyncChunkSave.store(self, pos, tag);
    }
}
