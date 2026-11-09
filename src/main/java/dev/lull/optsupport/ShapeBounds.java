package dev.lull.optsupport;

import net.minecraft.world.phys.AABB;

/** VoxelShapeBoundsMixin: a shape's bounds(), made once and kept on the shape (BlockEntityCullNoAllocMixin, 1.20.1). */
public interface ShapeBounds {
    AABB sleep$bounds();
}
