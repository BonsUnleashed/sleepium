package dev.lull.mixin;

import dev.lull.TickControl;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TickControl: a frozen level ticks only its chunk source, without ticking chunks (1.21's frozen tick: no time, no
 * scheduled ticks, no entities or block entities; forced chunks keep loading).
 */
@Mixin(ServerLevel.class)
public abstract class TickControlLevelMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sleep$frozen(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!TickControl.levelFrozen()) return;
        ((ServerLevel) (Object) this).getChunkSource().tick(hasTimeLeft, false);
        ci.cancel();
    }
}
