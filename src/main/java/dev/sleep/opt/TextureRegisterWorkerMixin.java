package dev.sleep.opt;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParallelRender: a texture registered from a worker job (a renderer's first getTexture of a new path) would be put in
 * the manager while its GL upload waits in the job's journal, and a job that then fails drops the journal: the texture
 * stays registered and never uploaded (black). A register inside a job hands the job to the render thread first.
 * Registering is rare: the inject's callback object does not matter here.
 */
@Mixin(TextureManager.class)
public abstract class TextureRegisterWorkerMixin {
    @Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V", at = @At("HEAD"))
    private void sleep$notInJobs(ResourceLocation path, AbstractTexture texture, CallbackInfo ci) {
        if (dev.sleep.optsupport.ParallelRender.inJob()) throw dev.sleep.optsupport.ParallelRender.REFUSE_NOT_READY;
    }
}
