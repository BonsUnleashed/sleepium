package dev.sleep.opt;

import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** WorldgenReactor: start (or get) a region's old-chunk scan, the future isOldChunkAround joins. */
@Mixin(IOWorker.class)
public interface ReactorIOWorkerInvoker {
    @Invoker("getOrCreateOldDataForRegion")
    CompletableFuture<BitSet> probe$oldData(int regionX, int regionZ);
}
