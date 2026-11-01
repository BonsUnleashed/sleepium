package dev.sleep.catmixin;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test only ({@code -Dsleep.test.createTime=T}): Create's render time pinned to T ticks, so frames of moving kinetic
 * scenes can be compared between runs and render paths (frame hashes).
 */
@Mixin(value = AnimationTickHolder.class, remap = false)
public abstract class CreateTimeTestMixin {
    private static final float T = Float.parseFloat(System.getProperty("sleep.test.createTime", "0"));

    @Inject(method = "getRenderTime(Lnet/minecraft/world/level/LevelAccessor;)F", at = @At("HEAD"), cancellable = true)
    private static void sleep$pinned(LevelAccessor level, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(T);
    }
}
