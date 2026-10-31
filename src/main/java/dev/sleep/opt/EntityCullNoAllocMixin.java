package dev.sleep.opt;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * exp/frame-garbage (client): EntityRenderer.shouldRender without the inflated AABB it makes per entity per frame.
 * The same doubles as inflate(0.5) and the AABB constructor (min / max of the pair), the same NaN / size / infinite
 * tests, then the frustum's own cube test on them; the leash fallback as vanilla. Renderers that override
 * shouldRender are not touched (their super call comes here).
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
        boolean infinite = Double.isInfinite(minX) && Double.isInfinite(minY) && Double.isInfinite(minZ) && Double.isInfinite(maxX)
                && Double.isInfinite(maxY) && Double.isInfinite(maxZ);
        if (infinite || ((FrustumInvoker) camera).sleep$cubeInFrustum(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        if (e instanceof Leashable leashable) {
            Entity holder = leashable.getLeashHolder();
            if (holder != null) return camera.isVisible(holder.getBoundingBoxForCulling());
        }
        return false;
    }
}
