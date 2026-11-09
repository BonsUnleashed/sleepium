package dev.lull.optsupport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.extensions.IForgeBlockEntity;

/**
 * BlockEntityCullNoAllocMixin's check mode (-Dlab.opt.beBoundsCheck=true, 1.20.1): every box also made by Forge's own
 * default code (IForgeBlockEntity.getRenderBoundingBox, copied word for word) and compared with equals().
 */
public final class BeBoundsCheck {
    private BeBoundsCheck() {}

    public static final boolean ON = Boolean.getBoolean("lab.opt.beBoundsCheck");
    public static long checked, mismatches;

    public static void check(BlockEntity be, AABB ours) {
        AABB forge = forge(be);
        checked++;
        if (!forge.equals(ours) && mismatches++ < 5) System.out.println("[sleep/opt] be bounds MISMATCH " + be.getBlockState() + " at " + be.getBlockPos() + ": " + ours + " vs Forge's " + forge);
        if ((checked & ((1 << 20) - 1)) == 0) System.out.println("[sleep/opt] be bounds check: " + checked + " checked, " + mismatches + " mismatches");
    }

    /** IForgeBlockEntity.getRenderBoundingBox (Forge 47.4.9) */
    private static AABB forge(BlockEntity self) {
        AABB bb = IForgeBlockEntity.INFINITE_EXTENT_AABB;
        BlockState state = self.getBlockState();
        Block block = state.getBlock();
        BlockPos pos = self.getBlockPos();
        if (block == Blocks.ENCHANTING_TABLE) {
            bb = new AABB(pos, pos.offset(1, 1, 1));
        } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
            bb = new AABB(pos.offset(-1, 0, -1), pos.offset(2, 2, 2));
        } else if (block == Blocks.STRUCTURE_BLOCK) {
            bb = IForgeBlockEntity.INFINITE_EXTENT_AABB;
        } else if (block != null && block != Blocks.BEACON) {
            AABB cbb = null;
            try {
                VoxelShape collisionShape = state.getCollisionShape(self.getLevel(), pos);
                if (!collisionShape.isEmpty()) cbb = collisionShape.bounds().move(pos);
            } catch (Exception e) {
                cbb = new AABB(pos.offset(-1, 0, -1), pos.offset(1, 1, 1));
            }
            if (cbb != null) bb = cbb;
        }
        return bb;
    }
}
