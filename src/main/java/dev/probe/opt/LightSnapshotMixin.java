package dev.probe.opt;

import dev.probe.optsupport.LightSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LightSnapshot: a light storage map remembers the keys it wrote since its last full copy (its only writes). */
@Mixin(DataLayerStorageMap.class)
public abstract class LightSnapshotMixin implements LightSnapshot.Tracked {
    @Unique private final LightSnapshot.Track probe$track = new LightSnapshot.Track();

    @Inject(method = "setLayer", at = @At("HEAD"))
    private void probe$set(long key, DataLayer array, CallbackInfo ci) {
        probe$track.written(key);
    }

    @Inject(method = "removeLayer", at = @At("HEAD"))
    private void probe$remove(long key, CallbackInfoReturnable<DataLayer> cir) {
        probe$track.written(key);
    }

    @Inject(method = "copyDataLayer", at = @At("HEAD"))
    private void probe$copy(long key, CallbackInfoReturnable<DataLayer> cir) {
        probe$track.written(key);
    }

    @Override
    public Long2ObjectOpenHashMap<DataLayer> probe$snapshot(Long2ObjectOpenHashMap<DataLayer> live) {
        return probe$track.snapshot(live);
    }
}
