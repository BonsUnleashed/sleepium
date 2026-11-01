package dev.sleep.optsupport;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * A block entity's renderer output kept in a parametric form by an adapter (KineticMemo: Create's rotating parts) and
 * drawn again from it while the object is unchanged: the renderer is not called. RenderCache asks it first.
 */
public interface BeMemo {
    /** true when this call drew the object (from the memo, or by recording it); false: the usual way. */
    boolean render(Object obj, Class<?> rendererClass, RenderCache.Draw draw, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay);
}
