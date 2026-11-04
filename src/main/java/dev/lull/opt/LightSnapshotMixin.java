package dev.lull.opt;

import dev.lull.optsupport.LightSnapshot;
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
    @Unique private final LightSnapshot.Track sleep$track = new LightSnapshot.Track();

    @Inject(method = "setLayer", at = @At("HEAD"))
    private void sleep$set(long key, DataLayer array, CallbackInfo ci) {
        sleep$track.written(key);
    }

    @Inject(method = "removeLayer", at = @At("HEAD"))
    private void sleep$remove(long key, CallbackInfoReturnable<DataLayer> cir) {
        sleep$track.written(key);
    }

    @Inject(method = "copyDataLayer", at = @At("HEAD"))
    private void sleep$copy(long key, CallbackInfoReturnable<DataLayer> cir) {
        sleep$track.written(key);
    }

    @Override
    public Long2ObjectOpenHashMap<DataLayer> sleep$snapshot(Long2ObjectOpenHashMap<DataLayer> live) {
        return sleep$track.snapshot(live);
    }
}
