package dev.sleep.opt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Virtual call to the protected BlockBehaviour.getCollisionShape (for ShapeCacheMixin's vanilla path). */
@Mixin(BlockBehaviour.class)
public interface BlockBehaviourInvoker {
    @Invoker("getCollisionShape")
    VoxelShape probe$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
}
