package dev.lull.opt;

import dev.lull.optsupport.ParallelRender;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ParallelEntitiesMixin: the worker renderer sets follow a resource reload. */
@Mixin(EntityRenderDispatcher.class)
public abstract class ParallelReloadMixin {
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void sleep$reloaded(ResourceManager rm, CallbackInfo ci) {
        ParallelRender.reloaded();
    }
}
