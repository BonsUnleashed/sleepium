package dev.sleep.opt;

import dev.sleep.optsupport.ClientLightAsync;
import dev.sleep.optsupport.LightVolumes;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ShaderLightMixin: a light change goes to the light volumes (a volume refresh, or the vanilla re-mesh with recipes). */
@Mixin(ClientChunkCache.class)
public abstract class ShaderLightChunkCacheMixin {
    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void sleep$light(LightLayer type, SectionPos pos, CallbackInfo ci) {
        if (ClientLightAsync.onWorker()) return;   // recorded there, replayed through LightVolumes
        LightVolumes.lightChanged(pos.x(), pos.y(), pos.z());
        ci.cancel();
    }
}
