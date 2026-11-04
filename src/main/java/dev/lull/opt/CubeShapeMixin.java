package dev.lull.opt;

import dev.lull.optsupport.CubeAt;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/cube-shape: every full block found by an entity collision query (BlockCollisions) is Shapes.block().move(x, y, z):
 * 4 objects and megamorphic coordinate reads for the collide math. It becomes one CubeAt, whose collision math is
 * vanilla's specialised to the unit cube (same values, bit for bit). Other shapes are moved as in vanilla.
 */
@Mixin(BlockCollisions.class)
public abstract class CubeShapeMixin {
    @Redirect(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape sleep$move(VoxelShape shape, double x, double y, double z) {
        return shape == Shapes.block() ? new CubeAt(x, y, z) : shape.move(x, y, z);
    }
}
