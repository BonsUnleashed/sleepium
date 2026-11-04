package dev.lull.opt;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * exp/frame-garbage (client): EntityRenderer.shouldRender without the inflated AABB it makes per entity per frame.
 * The same doubles as inflate(0.5) and the AABB constructor (min / max of the pair), the same NaN / size tests, then
 * the frustum's own tests on them: Forge's infinite-extent early exit (Frustum.isVisible: equal to
 * INFINITE_EXTENT_AABB) and the cube test. Renderers that override shouldRender are not touched (their super call
 * comes here). 1.20.1 has no leash fallback (1.21's Leashable).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityCullNoAllocMixin<T extends Entity> {
    /**
     * @author probe
     * @reason the same test without the inflated AABB per entity per frame (see the class comment); an overwrite, not a
     *     cancellable inject, which allocated a CallbackInfoReturnable per call
     */
    @Overwrite
    public boolean shouldRender(T e, Frustum camera, double camX, double camY, double camZ) {
        if (!e.shouldRender(camX, camY, camZ)) return false;
        if (e.noCulling) return true;
        AABB b = e.getBoundingBoxForCulling();
        double x1 = b.minX - 0.5, y1 = b.minY - 0.5, z1 = b.minZ - 0.5, x2 = b.maxX + 0.5, y2 = b.maxY + 0.5, z2 = b.maxZ + 0.5;
        double minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2), maxX = Math.max(x1, x2), maxY = Math.max(y1, y2),
                maxZ = Math.max(z1, z2);
        boolean nan = Double.isNaN(minX) || Double.isNaN(minY) || Double.isNaN(minZ) || Double.isNaN(maxX) || Double.isNaN(maxY) || Double.isNaN(maxZ);
        if (nan || ((maxX - minX) + (maxY - minY) + (maxZ - minZ)) / 3.0 == 0.0) {
            double px = e.getX(), py = e.getY(), pz = e.getZ();
            minX = Math.min(px - 2.0, px + 2.0);
            minY = Math.min(py - 2.0, py + 2.0);
            minZ = Math.min(pz - 2.0, pz + 2.0);
            maxX = Math.max(px - 2.0, px + 2.0);
            maxY = Math.max(py - 2.0, py + 2.0);
            maxZ = Math.max(pz - 2.0, pz + 2.0);
        }
        // AABB.equals compares with Double.compare: the infinities themselves
        if (minX == Double.NEGATIVE_INFINITY && minY == Double.NEGATIVE_INFINITY && minZ == Double.NEGATIVE_INFINITY && maxX == Double.POSITIVE_INFINITY
                && maxY == Double.POSITIVE_INFINITY && maxZ == Double.POSITIVE_INFINITY) return true;
        return ((FrustumInvoker) camera).sleep$cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
