package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): block writes on the main thread while a genbench runs, split into those that change the
 * state and those that write the state already there — during post-processing ("settle") and in general.
 */
@Mixin(Level.class)
public abstract class GenProfileSettleMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean sleep$set(BlockPos pos, BlockState state, int flags, int recursionLeft, Operation<Boolean> original) {
        if (!GenProfile.active() || ((Level) (Object) this).isClientSide) return original.call(pos, state, flags, recursionLeft);
        boolean changed = original.call(pos, state, flags, recursionLeft);
        GenProfile.add(changed ? "main: setBlock changed a block" : "main: setBlock wrote the same state", 0, 0);
        return changed;
    }
}
