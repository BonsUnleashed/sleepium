package testmods.foreign;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stand-in for another mod's mixin (package outside dev.probe on purpose): a no-op inject into the furnace tick.
 * Only applied with -Dprobe.foreignTest=true. The sleep engine must then refuse furnaces ("mixin-changed").
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ForeignFurnaceMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void foreign$tick(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity be, CallbackInfo ci) {
    }
}
