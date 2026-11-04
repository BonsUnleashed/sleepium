package dev.lull.opt;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenReactor: the chunk storage's IOWorker (ChunkMap extends ChunkStorage). 1.20.1 has no RegionStorageInfo. */
@Mixin(ChunkStorage.class)
public interface ReactorStorageAccessor {
    @Accessor("worker")
    IOWorker sleep$worker();
}
