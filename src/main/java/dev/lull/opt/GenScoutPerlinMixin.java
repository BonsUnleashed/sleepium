package dev.lull.opt;

import dev.lull.optsupport.GenScout;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dlab.genscout=true): ImprovedNoise.noise calls per thread, attributed to phases by GenProfile. */
@Mixin(ImprovedNoise.class)
public abstract class GenScoutPerlinMixin {
    @Inject(method = "noise(DDDDD)D", at = @At("HEAD"))
    private void sleep$count(double x, double y, double z, double yScale, double yMax, CallbackInfoReturnable<Double> cir) {
        GenScout.perlin();
    }
}
