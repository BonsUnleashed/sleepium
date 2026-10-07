package dev.probe.opt;

import dev.probe.optsupport.ChunkIo;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** ChunkIo: an IOWorker's pending store keeps its encoded bytes beside (then instead of) the tag. */
@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore")
public abstract class ChunkIoPendingMixin implements ChunkIo.Entry {
    @Shadow CompoundTag data;
    @Shadow @Final CompletableFuture<Void> result;
    @Unique private byte[] probe$bytes;

    @Override
    public CompoundTag probe$data() {
        return data;
    }

    @Override
    public void probe$setData(CompoundTag data) {
        this.data = data;
    }

    @Override
    public byte[] probe$bytes() {
        return probe$bytes;
    }

    @Override
    public void probe$setBytes(byte[] bytes) {
        probe$bytes = bytes;
    }

    @Override
    public CompletableFuture<Void> probe$result() {
        return result;
    }
}
