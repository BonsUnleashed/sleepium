package dev.probe.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ParallelEntitiesMixin: vanilla's renderEntity, for the jobs kept on the render thread. */
@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("renderEntity")
    void probe$renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource);
}
