package dev.probe.opt;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** EntityCullNoAllocMixin: the frustum's cube test on plain coordinates. */
@Mixin(Frustum.class)
public interface FrustumInvoker {
    @Invoker("cubeInFrustum")
    boolean probe$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
