package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.optsupport.GenScout;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (lab.genscout): Aquifer.NoiseBasedAquifer.computeFluid calls and time. */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class GenScoutFluidMixin {
    @WrapMethod(method = "computeFluid")
    private Aquifer.FluidStatus sleep$time(int x, int y, int z, Operation<Aquifer.FluidStatus> original) {
        long t = System.nanoTime();
        try {
            return original.call(x, y, z);
        } finally {
            GenScout.FLUID_NANOS.add(System.nanoTime() - t);
            GenScout.FLUID_CALLS.increment();
        }
    }
}
