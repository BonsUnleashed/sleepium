package dev.lull.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientAuto runs only (-Dsleep.clientAuto): the mouse does not turn the camera, click or scroll, so touching the mouse
 * during a measurement changes nothing (the frame hash compares camera views; a moved camera looked like a render
 * change). Keyboard input is untouched.
 */
@Mixin(MouseHandler.class)
public abstract class ClientAutoMouseMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void sleep$noTurn(double movementTime, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void sleep$noPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void sleep$noScroll(long window, double x, double y, CallbackInfo ci) {
        ci.cancel();
    }
}
