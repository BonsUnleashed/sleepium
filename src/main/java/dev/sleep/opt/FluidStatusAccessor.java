package dev.sleep.opt;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** WorldgenSkip: Aquifer.FluidStatus's package-private level and fluid, to compare statuses by value. */
@Mixin(Aquifer.FluidStatus.class)
public interface FluidStatusAccessor {
    @Accessor("fluidLevel")
    int probe$level();

    @Accessor("fluidType")
    BlockState probe$type();
}
