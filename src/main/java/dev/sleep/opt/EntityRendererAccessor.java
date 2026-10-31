package dev.sleep.opt;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ParallelEntitiesMixin: the shadow strength the dispatcher reads from a renderer. */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("shadowStrength")
    float probe$shadowStrength();

    @org.spongepowered.asm.mixin.gen.Invoker("getShadowRadius")
    float probe$shadowRadius(net.minecraft.world.entity.Entity entity);
}
