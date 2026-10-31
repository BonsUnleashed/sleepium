package dev.sleep.opt;

import dev.sleep.optsupport.CubeAt;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Goes with CubeShapeMixin: VoxelShape.shape (the discrete shape of Shapes.block() for CubeAt). */
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor extends CubeAt.ShapeAccess {
    @Override
    @Accessor("shape")
    DiscreteVoxelShape probe$shape();
}
