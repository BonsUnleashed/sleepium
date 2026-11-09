package dev.lull.mixin;

import dev.lull.TickControl;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TickControl (the test tools' /tick on 1.20.1): a pending step is taken at the start of each server tick. */
@Mixin(MinecraftServer.class)
public abstract class TickControlServerMixin {
    @Shadow private int tickCount;
    @Shadow private long nextTickTime;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void sleep$tickBegin(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        TickControl.beginTick();
    }

    /**
     * A frozen tick without a step leaves the tick count as it was: mobs pick goals by (tick count + id) % 2, and how
     * many frozen ticks pass between the tools' commands depends on the clock (1.21's /tick freeze counts them; here
     * the tests need the count to move with the stepped ticks only).
     */
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void sleep$tickEnd(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (TickControl.levelFrozen()) tickCount--;
        if (TickControl.sprinting()) nextTickTime = net.minecraft.Util.getMillis();   // the wait after this tick ends at once
    }
}
