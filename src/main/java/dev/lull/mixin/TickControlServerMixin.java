package dev.lull.mixin;

import dev.lull.TickControl;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TickControl (the test tools' /tick on 1.20.1): a pending step is taken at the start of each server tick. */
@Mixin(MinecraftServer.class)
public abstract class TickControlServerMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void sleep$tickBegin(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TickControl.beginTick();
    }
}
