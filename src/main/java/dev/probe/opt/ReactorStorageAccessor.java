package dev.probe.opt;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** WorldgenReactor: the chunk storage's IOWorker (ChunkMap extends ChunkStorage). */
@Mixin(ChunkStorage.class)
public interface ReactorStorageAccessor {
    @Accessor("worker")
    IOWorker probe$worker();

    @Invoker("storageInfo")
    net.minecraft.world.level.chunk.storage.RegionStorageInfo probe$storageInfo();
}
