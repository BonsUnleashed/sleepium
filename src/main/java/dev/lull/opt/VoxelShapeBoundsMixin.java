package dev.lull.opt;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * BlockEntityCullNoAllocMixin (1.20.1): a shape's bounds() made once and kept on the shape. A VoxelShape never changes
 * and an AABB cannot be changed, so the kept box is the one bounds() would make again. Only the culling asks for it
 * (bounds() itself stays vanilla's); two threads at once make the same box twice, either one is kept.
 */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeBoundsMixin implements dev.lull.optsupport.ShapeBounds {
    @Shadow public abstract AABB bounds();

    @Unique private AABB sleep$bounds;

    @Override
    public AABB sleep$bounds() {
        AABB b = sleep$bounds;
        if (b == null) sleep$bounds = b = bounds();
        return b;
    }
}
