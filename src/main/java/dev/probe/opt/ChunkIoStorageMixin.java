package dev.probe.opt;

import dev.probe.optsupport.ChunkIo;
import java.io.IOException;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** ChunkIo: the IOWorker's writer opens region files through the storage's own cache. */
@Mixin(RegionFileStorage.class)
public abstract class ChunkIoStorageMixin implements ChunkIo.Storage {
    @Shadow
    private RegionFile getRegionFile(ChunkPos chunkPos) throws IOException {
        throw new AssertionError();
    }

    @Override
    public RegionFile probe$region(ChunkPos pos) throws IOException {
        return getRegionFile(pos);
    }
}
