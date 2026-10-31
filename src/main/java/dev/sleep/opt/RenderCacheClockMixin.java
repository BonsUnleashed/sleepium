package dev.sleep.opt;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.Blaze3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The render cache's animation probe also moves the wall clock (GLFW time): renderers that animate by it (GeckoLib's
 * block entities: RenderUtil.getCurrentTick() = Blaze3D.getTime() * 20) otherwise record the same output twice and are
 * taken for static. Only the probing thread sees the shift; one reference compare per call otherwise.
 */
@Mixin(Blaze3D.class)
public abstract class RenderCacheClockMixin {
    @ModifyReturnValue(method = "getTime", at = @At("RETURN"))
    private static double sleep$probeShift(double t) {
        return dev.sleep.optsupport.RenderCache.clockShiftThread == Thread.currentThread() ? t + dev.sleep.optsupport.RenderCache.CLOCK_SHIFT : t;
    }
}
