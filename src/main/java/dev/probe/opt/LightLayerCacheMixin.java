package dev.probe.opt;

import dev.probe.optsupport.OptCounters;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/light-layer-cache (server and client): DataLayerStorageMap.getLayer behind vanilla's own 2-entry cache looks
 * the section up in the whole map (every section of every loaded chunk) — and a missing layer is never cached, so the
 * light propagation's "does this neighbour section store light" asks the big map again and again (~15 % of the light
 * work while relighting, JFR). Here that map lookup goes through a 256-entry direct-mapped cache of the map's current
 * content, present and absent keys alike, kept exact by updating the one affected slot on every write of the map
 * (setLayer, removeLayer, copyDataLayer — the only ones). Vanilla's 2-entry cache in front, with its quirks (not
 * cleared on setLayer / removeLayer), is untouched. Only for maps with the cache enabled (the updating copy, one
 * thread); the visible copy (cache disabled, read by other threads) goes straight to the map.
 * {@code -Dlab.lightcache.verify=true}: every hit compared with the map.
 */
@Mixin(DataLayerStorageMap.class)
public abstract class LightLayerCacheMixin {
    @Shadow private boolean cacheEnabled;
    @Unique private static final boolean VERIFY = Boolean.getBoolean("lab.lightcache.verify");
    @Unique private final long[] probe$keys = new long[256];
    @Unique private final DataLayer[] probe$vals = new DataLayer[256];
    @Unique private final boolean[] probe$used = new boolean[256];

    @Unique
    private static int probe$slot(long key) {
        return (int) HashCommon.mix(key) & 255;
    }

    @Redirect(method = "getLayer", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;get(J)Ljava/lang/Object;"))
    private Object probe$get(Long2ObjectOpenHashMap<DataLayer> map, long key) {
        if (!cacheEnabled) return map.get(key);
        int i = probe$slot(key);
        if (probe$used[i] && probe$keys[i] == key) {
            DataLayer v = probe$vals[i];
            if (VERIFY && map.get(key) != v && OptCounters.MEMO_MISMATCHES.incrementAndGet() <= 5)
                System.out.println("[probe/opt] light layer cache MISMATCH at " + key);
            return v;
        }
        DataLayer v = map.get(key);
        probe$keys[i] = key;
        probe$vals[i] = v;
        probe$used[i] = true;
        return v;
    }

    @Unique
    private void probe$written(long key, DataLayer now) {
        int i = probe$slot(key);
        if (probe$used[i] && probe$keys[i] == key) probe$vals[i] = now;
    }

    @Inject(method = "setLayer", at = @At("RETURN"))
    private void probe$set(long key, DataLayer array, CallbackInfo ci) {
        probe$written(key, array);
    }

    @Inject(method = "removeLayer", at = @At("RETURN"))
    private void probe$remove(long key, CallbackInfoReturnable<DataLayer> cir) {
        probe$written(key, null);
    }

    @Inject(method = "copyDataLayer", at = @At("RETURN"))
    private void probe$copy(long key, CallbackInfoReturnable<DataLayer> cir) {
        probe$written(key, cir.getReturnValue());
    }
}
