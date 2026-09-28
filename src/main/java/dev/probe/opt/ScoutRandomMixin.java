package dev.probe.opt;

import dev.probe.optsupport.ParallelTickScout;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** exp/parallel-tick-scout (measurement only): draws from the level's RNG. */
@Mixin(LegacyRandomSource.class)
public abstract class ScoutRandomMixin {
    @Inject(method = "next", at = @At("HEAD"))
    private void probe$draw(int bits, CallbackInfoReturnable<Integer> cir) {
        ParallelTickScout.rngDraw(this);
    }
}
