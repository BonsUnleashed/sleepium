package dev.sleep.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleep.optsupport.ParallelRender;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/parallel-render (client): the entity loop of renderLevel queues its entities (ParallelRender starts the eligible
 * ones on workers at once) and the queue is drained in order right after the loop, before the batch is ended.
 */
@Mixin(LevelRenderer.class)
public abstract class ParallelEntitiesMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void probe$queue(LevelRenderer self, Entity e, double camX, double camY, double camZ, float pt, PoseStack pose, MultiBufferSource buffers) {
        ParallelRender.enqueue(self, e, camX, camY, camZ, pt, pose, buffers);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void probe$drain(CallbackInfo ci) {
        ParallelRender.drain((LevelRenderer) (Object) this);
    }
}
