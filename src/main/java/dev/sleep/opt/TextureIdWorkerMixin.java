package dev.sleep.opt;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ParallelRender: AbstractTexture.getId() inside a worker job. The id of a texture that already has one is a field
 * read (the render-thread assert is then skipped); one without an id needs GL: the job is handed to the render thread.
 */
@Mixin(AbstractTexture.class)
public abstract class TextureIdWorkerMixin {
    @Shadow protected int id;

    @WrapWithCondition(method = "getId", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V"))
    private boolean probe$assertOutsideJobs() {
        if (!dev.sleep.optsupport.ParallelRender.inJob()) return true;
        if (this.id == -1) throw dev.sleep.optsupport.ParallelRender.REFUSE_NOT_READY;
        return false;
    }
}
