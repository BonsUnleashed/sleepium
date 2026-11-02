package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.optsupport.LightVolumes;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ShaderLightMixin: a light change goes to the light volumes (a volume refresh, or the vanilla re-mesh with recipes)
 * instead of vanilla's re-mesh. Only the re-mesh call is replaced: other mods' hooks on onLightUpdate still see every
 * light change (a cancel at its head hid them from the ones after it).
 */
@Mixin(ClientChunkCache.class)
public abstract class ShaderLightChunkCacheMixin {
    @WrapOperation(method = "onLightUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;setSectionDirty(III)V"))
    private void sleep$light(LevelRenderer renderer, int x, int y, int z, Operation<Void> original) {
        LightVolumes.lightChanged(x, y, z);
    }
}
