package dev.sleep.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** ParallelEntitiesMixin: the parts of EntityRenderDispatcher.render a worker repeats. */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherInvoker {
    @Accessor("shouldRenderShadow")
    boolean sleep$shouldRenderShadow();

    @Invoker("renderShadow")
    static void sleep$renderShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float weight, float partialTicks, LevelReader level, float size) {
        throw new AssertionError();
    }
}
