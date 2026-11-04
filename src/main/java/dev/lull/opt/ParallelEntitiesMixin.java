package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.optsupport.ParallelRender;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/parallel-render (client): the entity loop of renderLevel queues its entities (ParallelRender starts the eligible
 * ones on workers at once) and the queue is drained in order right after the loop, before the batch is ended; a
 * worker job is merged through renderEntity (its dispatcher call: ParallelRender.merge).
 */
@Mixin(LevelRenderer.class)
public abstract class ParallelEntitiesMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void sleep$queue(LevelRenderer self, Entity e, double camX, double camY, double camZ, float pt, PoseStack pose, MultiBufferSource buffers) {
        ParallelRender.enqueue(self, e, camX, camY, camZ, pt, pose, buffers);
    }

    /** the merge of a worker job goes through vanilla's renderEntity: its dispatcher call takes the job's bytes */
    @Redirect(method = "renderEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void sleep$merge(EntityRenderDispatcher d, Entity e, double x, double y, double z, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!ParallelRender.merge(e, x, y, z, yaw, pt, pose, buffers, light)) d.render(e, x, y, z, yaw, pt, pose, buffers, light);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void sleep$drain(CallbackInfo ci) {
        // (1.21.1: -Dsleep.drawStats sections around the drain; the renderer's measurement is not on 1.20.1 yet)
        ParallelRender.drain((LevelRenderer) (Object) this);
    }
}
