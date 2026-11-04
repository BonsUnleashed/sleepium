package dev.lull.opt;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * ParallelEntitiesMixin: the shadow strength and radius the dispatcher reads from a renderer (1.20.1: both fields; a
 * renderer may set the radius in its render, as SlimeRenderer does by size, so it is read after the render).
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Accessor("shadowStrength")
    float sleep$shadowStrength();

    @Accessor("shadowRadius")
    float sleep$shadowRadius();
}
