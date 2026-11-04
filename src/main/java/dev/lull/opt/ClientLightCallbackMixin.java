package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.optsupport.ClientLightAsync;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ClientLightAsyncMixin: the light engine's "section changed" callback, recorded while the engine runs on the light
 * worker and replayed on the render thread through ClientChunkCache.onLightUpdate itself - so the callback, and every
 * other mod's hook on it (Flywheel keeps its light updates in a plain set), runs on the render thread as in vanilla.
 * (Until 11-02 it was cancelled at the head of onLightUpdate and replayed as setSectionDirty: a hook that ran before
 * ours ran on the worker - Flywheel's set was then written while its tasks iterated it, a hang - and one after ours
 * never ran: Create's visuals kept stale light.)
 */
@Mixin(LayerLightSectionStorage.class)
public abstract class ClientLightCallbackMixin {
    @WrapOperation(method = "swapSectionMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LightChunkGetter;onLightUpdate(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)V"))
    private void sleep$record(LightChunkGetter source, LightLayer layer, SectionPos pos, Operation<Void> original) {
        if (ClientLightAsync.onWorker()) ClientLightAsync.record(layer, pos);
        else original.call(source, layer, pos);
    }
}
