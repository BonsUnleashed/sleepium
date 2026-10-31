package dev.sleep.opt;

import dev.sleep.optsupport.sleep.MoveMemo;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** lab.opt.MoveMemo: draws from the random of the entity whose move is being recorded. */
@Mixin(LegacyRandomSource.class)
public abstract class MoveMemoRandomMixin {
    @Inject(method = "next", at = @At("HEAD"))
    private void sleep$draw(int bits, CallbackInfoReturnable<Integer> cir) {
        MoveMemo.draw(this);
    }
}
