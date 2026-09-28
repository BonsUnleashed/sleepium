package dev.probe.opt;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * WorldgenReactor (chunk loads): vanilla's scheduleChunkLoad hops to the main thread twice for every chunk it starts —
 * thenApplyAsync(main) to create or deserialize it, then exceptionallyAsync(main), which the JDK runs on its executor
 * even for a normal result — and each hop waited ~140 ms in the main queue while generating: the start of every chunk's
 * generation gated on the main thread.
 *
 * <p>Here, a chunk with no saved data is created on the pool (a new ProtoChunk: plain construction); its
 * chunkTypeCache mark (a main-thread map) goes into a queue the main thread drains at its next executor poll — until then
 * the only reader, isExistingChunkFull, reads the disk and finds nothing: the same answer ("not full"). Saved data is
 * deserialized on the pool by vanilla's read, its shared-state calls replayed on the main thread in order before the
 * chunk is published (ReadJournal; a tag with mod attachments reads on main as in vanilla); failures go to vanilla's
 * handler on the main thread; a normal result does not hop.
 */
@Mixin(ChunkMap.class)
public abstract class ReactorChunkLoadMixin implements dev.probe.optsupport.ChunkLoadMarks {
    @Shadow @Final ServerLevel level;
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;
    @Shadow @Final private PoiManager poiManager;
    @Shadow @Final private Long2ByteMap chunkTypeCache;

    @Shadow
    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isChunkDataValid(CompoundTag tag) {
        throw new AssertionError();
    }

    @Shadow
    private ChunkAccess handleChunkLoadFailure(Throwable exception, ChunkPos chunkPos) {
        throw new AssertionError();
    }

    @Shadow
    private byte markPosition(ChunkPos chunkPos, ChunkType chunkType) {
        throw new AssertionError();
    }

    @Unique private final ConcurrentLinkedQueue<Long> probe$replaceable = new ConcurrentLinkedQueue<>();

    @Override
    public void probe$drainMarks() {
        Long p;
        while ((p = probe$replaceable.poll()) != null) this.chunkTypeCache.put(p.longValue(), (byte) -1);   // markPositionReplaceable
    }

    /**
     * @author probe
     * @reason new chunks are created off the main thread, and a normal result does not hop to it (see class doc)
     */
    @Overwrite
    private CompletableFuture<ChunkAccess> scheduleChunkLoad(ChunkPos chunkPos) {
        return this.readChunk(chunkPos).thenApply(p_214925_ -> p_214925_.filter(p_214928_ -> {
            boolean flag = isChunkDataValid(p_214928_);
            if (!flag) {
                org.slf4j.LoggerFactory.getLogger(ChunkMap.class).error("Chunk file at {} is missing level data, skipping", chunkPos);
            }
            return flag;
        })).thenCompose(tag -> {
            if (tag.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> {
                    probe$replaceable.add(chunkPos.toLong());
                    return (ChunkAccess) new ProtoChunk(chunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registries.BIOME), null);
                }, Util.backgroundExecutor());
            }
            CompoundTag saved = tag.get();
            if (!dev.probe.optsupport.ReadJournal.ENABLED
                    || saved.contains(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY, 10)) {
                // mod attachment deserializers run in the read: keep it on the main thread
                return CompletableFuture.supplyAsync(() -> {
                    this.level.getProfiler().incrementCounter("chunkLoad");
                    dev.probe.optsupport.ReadJournal.ON_MAIN.increment();
                    ChunkAccess chunkaccess = ChunkSerializer.read(this.level, this.poiManager, ((ReactorStorageAccessor) (Object) this).probe$storageInfo(), chunkPos, saved);
                    this.markPosition(chunkPos, chunkaccess.getPersistedStatus().getChunkType());
                    return chunkaccess;
                }, this.mainThreadExecutor);
            }
            // saved data: the read runs on the pool over the saved tag; its shared-state calls replay on main (ReadJournal)
            return CompletableFuture.supplyAsync(() -> dev.probe.optsupport.ReadJournal.read(() -> ChunkSerializer.read(this.level, this.poiManager,
                    ((ReactorStorageAccessor) (Object) this).probe$storageInfo(), chunkPos, saved)), Util.backgroundExecutor()).thenApplyAsync(journal -> {
                this.level.getProfiler().incrementCounter("chunkLoad");
                ChunkAccess chunkaccess = journal.publish();
                this.markPosition(chunkPos, chunkaccess.getPersistedStatus().getChunkType());
                return chunkaccess;
            }, this.mainThreadExecutor);
        }).handle((chunk, error) -> error == null ? CompletableFuture.completedFuture(chunk)
                : CompletableFuture.supplyAsync(() -> this.handleChunkLoadFailure(error, chunkPos), this.mainThreadExecutor))
          .thenCompose(f -> f);
    }
}
