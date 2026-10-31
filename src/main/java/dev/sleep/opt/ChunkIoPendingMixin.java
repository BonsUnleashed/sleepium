package dev.sleep.opt;

import dev.sleep.optsupport.ChunkIo;
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
    @Unique private byte[] sleep$bytes;

    @Override
    public CompoundTag sleep$data() {
        return data;
    }

    @Override
    public void sleep$setData(CompoundTag data) {
        this.data = data;
    }

    @Override
    public byte[] sleep$bytes() {
        return sleep$bytes;
    }

    @Override
    public void sleep$setBytes(byte[] bytes) {
        sleep$bytes = bytes;
    }

    @Override
    public CompletableFuture<Void> sleep$result() {
        return result;
    }
}
