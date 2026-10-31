package dev.sleep.opt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.optsupport.GenScout;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (lab.genscout): Aquifer.computeSubstance paths and results. */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class GenScoutAquiferMixin {
    @WrapMethod(method = "computeSubstance")
    private BlockState probe$count(DensityFunction.FunctionContext ctx, double substance, Operation<BlockState> original) {
        BlockState s = original.call(ctx, substance);
        if (substance > 0.0) GenScout.AQ_SOLID.increment();
        else {
            if (ctx.blockY() >= 63) GenScout.AQ_HIGH.increment();
            if (s == null) GenScout.AQ_NULL.increment();
            else if (s.isAir()) GenScout.AQ_AIR.increment();
            else if (s.is(Blocks.WATER)) GenScout.AQ_WATER.increment();
            else if (s.is(Blocks.LAVA)) GenScout.AQ_LAVA.increment();
            else GenScout.AQ_OTHER.increment();
        }
        return s;
    }
}
