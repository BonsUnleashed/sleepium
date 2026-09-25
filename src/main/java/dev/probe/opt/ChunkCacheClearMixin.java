package dev.probe.opt;

import dev.probe.optsupport.ChunkCacheHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drop ChunkCacheMixin's entry whenever vanilla clears its own chunk cache (status changes, unloads). */
@Mixin(ServerChunkCache.class)
public abstract class ChunkCacheClearMixin {
    @Shadow @Final public ServerLevel level;

    @Inject(method = "clearCache", at = @At("TAIL"))
    private void probe$clear(CallbackInfo ci) {
        if (level != null) ((ChunkCacheHolder) level).probe$ccReset();
    }
}
