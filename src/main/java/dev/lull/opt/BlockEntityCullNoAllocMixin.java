package dev.lull.opt;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.extensions.IForgeBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/frame-garbage (client), 1.20.1: Forge culls block entities with getRenderBoundingBox(), whose default
 * (IForgeBlockEntity) is built every frame for every block entity in view - two BlockPos and an AABB for a chest, the
 * collision shape's bounds (a new AABB, the shape's coordinate lists) and a moved copy for the others: a fifth of the
 * render thread's allocation on a block-entity scene. This is that default as BlockEntity's own method (block
 * entities that override it keep theirs): the same boxes, each one AABB made from the coordinates; a collision shape's
 * bounds are made once per shape (VoxelShapeBoundsMixin: a shape never changes) and moved. Same values in the same
 * order of operations, so the same box. (1.21.1: NeoForge's check is another one - BlockEntityCullNoAllocMixin there.)
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityCullNoAllocMixin {
    @Shadow public abstract BlockState getBlockState();

    @Shadow public abstract BlockPos getBlockPos();

    @Shadow public abstract Level getLevel();

    public AABB getRenderBoundingBox() {
        AABB bb = IForgeBlockEntity.INFINITE_EXTENT_AABB;
        BlockState state = getBlockState();
        Block block = state.getBlock();
        BlockPos pos = getBlockPos();
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (block == Blocks.ENCHANTING_TABLE) {
            bb = new AABB(x, y, z, x + 1, y + 1, z + 1);   // new AABB(pos, pos.offset(1, 1, 1))
        } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            bb = new AABB(x - 1, y, z - 1, x + 2, y + 2, z + 2);   // new AABB(pos.offset(-1, 0, -1), pos.offset(2, 2, 2))
        } else if (block == Blocks.STRUCTURE_BLOCK) {
            bb = IForgeBlockEntity.INFINITE_EXTENT_AABB;
        } else if (block != null && block != Blocks.BEACON) {
            AABB cbb = null;
            try {
                VoxelShape collisionShape = state.getCollisionShape(getLevel(), pos);
                if (!collisionShape.isEmpty()) cbb = ((dev.lull.optsupport.ShapeBounds) collisionShape).sleep$bounds().move(pos);
            } catch (Exception e) {
                // Forge's fallback when the block entity arrives before its chunk (new AABB(pos.offset(-1, 0, -1), pos.offset(1, 1, 1)))
                cbb = new AABB(x - 1, y, z - 1, x + 1, y + 1, z + 1);
            }
            if (cbb != null) bb = cbb;
        }
        if (dev.lull.optsupport.BeBoundsCheck.ON) dev.lull.optsupport.BeBoundsCheck.check((BlockEntity) (Object) this, bb);
        return bb;
    }
}
