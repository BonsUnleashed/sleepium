package dev.lull.opt;

import dev.lull.optsupport.ChunkIo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionBitmap;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ChunkIo: a region file opened for sync writes is opened without DSYNC; the header write is synced on both sides
 * instead (force before it: the data it points at is on the device; force after it: the header is) — vanilla's single
 * write keeps its guarantee with the same two waits, and a batch pays them once for all its chunks.
 */
@Mixin(RegionFile.class)
public abstract class ChunkIoRegionMixin implements ChunkIo.Region {
    @Shadow @Final private FileChannel file;
    @Shadow @Final private IntBuffer offsets;
    @Shadow @Final private IntBuffer timestamps;
    @Shadow @Final protected RegionBitmap usedSectors;
    @Shadow @Final RegionFileVersion version;

    @Unique private boolean sleep$sync;

    @Shadow
    private void writeHeader() throws IOException {
        throw new AssertionError();
    }

    @Shadow
    private int packSectorOffset(int sectorOffset, int sectorCount) {
        throw new AssertionError();
    }

    @Shadow
    private static int getOffsetIndex(ChunkPos chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    private static int sizeToSectors(int size) {
        throw new AssertionError();
    }

    @Shadow
    private static int getTimestamp() {
        throw new AssertionError();
    }

    @Shadow
    private static int getSectorNumber(int packedSectorOffset) {
        throw new AssertionError();
    }

    @Shadow
    private static int getNumSectors(int packedSectorOffset) {
        throw new AssertionError();
    }

    @Shadow
    private Path getExternalChunkPath(ChunkPos chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void write(ChunkPos chunkPos, ByteBuffer chunkData) throws IOException;

    // 1.20.1: the constructor has no RegionStorageInfo, and opens the channel in one of two calls (with or without DSYNC)
    @Redirect(method = "<init>(Ljava/nio/file/Path;Ljava/nio/file/Path;Lnet/minecraft/world/level/chunk/storage/RegionFileVersion;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/nio/channels/FileChannel;open(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/nio/channels/FileChannel;"))
    private FileChannel sleep$open(Path path, OpenOption[] options) throws IOException {
        int n = 0;
        OpenOption[] kept = new OpenOption[options.length];
        for (OpenOption o : options) {
            if (o == StandardOpenOption.DSYNC) sleep$sync = true;
            else kept[n++] = o;
        }
        return FileChannel.open(path, java.util.Arrays.copyOf(kept, n));
    }

    @Inject(method = "writeHeader", at = @At("HEAD"))
    private void sleep$syncData(CallbackInfo ci) throws IOException {
        if (sleep$sync) file.force(true);
    }

    @Inject(method = "writeHeader", at = @At("TAIL"))
    private void sleep$syncHeader(CallbackInfo ci) throws IOException {
        if (sleep$sync) file.force(true);
    }

    @Override
    public int sleep$versionId() {
        return version.getId();
    }

    @Override
    public synchronized void sleep$writeBatch(ChunkPos[] pos, byte[][] data, int n) throws IOException {
        int[] index = new int[n], packed = new int[n], old = new int[n];
        Path[] external = new Path[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            byte[] record = data[i];
            int sectors = sizeToSectors(record.length);
            // 1.21.1 emits JvmProfiler.onRegionFileWrite here as vanilla's write does; 1.20.1's RegionFile has no such event
            if (sectors >= 256) {   // an oversized chunk: vanilla's own path (its external file, the stub)
                write(pos[i], ByteBuffer.wrap(record));
                continue;
            }
            int start = usedSectors.allocate(sectors);
            file.write(ByteBuffer.wrap(record), (long) start * 4096L);
            index[m] = getOffsetIndex(pos[i]);
            old[m] = offsets.get(index[m]);
            packed[m] = packSectorOffset(start, sectors);
            external[m] = getExternalChunkPath(pos[i]);
            m++;
        }
        if (m == 0) return;
        int now = getTimestamp();
        for (int i = 0; i < m; i++) {
            offsets.put(index[i], packed[i]);
            timestamps.put(index[i], now);
        }
        writeHeader();   // synced before (the sectors) and after (the header) when sync writes are on
        for (int i = 0; i < m; i++) {
            Files.deleteIfExists(external[i]);   // vanilla's commit op: an older oversized copy goes
            if (old[i] != 0) usedSectors.free(getSectorNumber(old[i]), getNumSectors(old[i]));
        }
    }
}
