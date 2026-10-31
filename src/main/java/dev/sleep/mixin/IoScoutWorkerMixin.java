package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.IoScout;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Measurement only (-Dsleep.ioScout=true): the IOWorker queue: stores posted, loads, each chunk written and the backlog. */
@Mixin(IOWorker.class)
public abstract class IoScoutWorkerMixin {
    @Shadow @Final private Map<ChunkPos, ?> pendingWrites;
    @Shadow public abstract RegionStorageInfo storageInfo();

    @WrapMethod(method = "store")
    private java.util.concurrent.CompletableFuture<Void> sleep$store(ChunkPos pos, net.minecraft.nbt.CompoundTag data, Operation<java.util.concurrent.CompletableFuture<Void>> original) {
        IoScout.stored(storageInfo().type());
        return original.call(pos, data);
    }

    @WrapMethod(method = "loadAsync")
    private java.util.concurrent.CompletableFuture<java.util.Optional<net.minecraft.nbt.CompoundTag>> sleep$load(ChunkPos pos,
            Operation<java.util.concurrent.CompletableFuture<java.util.Optional<net.minecraft.nbt.CompoundTag>>> original) {
        IoScout.loaded(storageInfo().type());
        return original.call(pos);
    }

    // PendingStore is private: plain injects without the arguments (runStore catches everything, so RETURN always runs)
    @org.spongepowered.asm.mixin.injection.Inject(method = "runStore", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void sleep$runStoreStart(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        IoScout.beginWrite(storageInfo().type());
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "runStore", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void sleep$runStoreEnd(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        IoScout.endWrite(pendingWrites.size());
    }
}
