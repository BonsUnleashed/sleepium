package dev.probe.mixin;

import dev.probe.optsupport.GenTrace;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dlab.pgen.trace=true): each server tick's wall time (tickServer). */
@Mixin(MinecraftServer.class)
public abstract class GenTraceTickMixin {
    @Unique private long probe$t0;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void probe$start(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        probe$t0 = System.nanoTime();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void probe$end(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        GenTrace.tick(System.nanoTime() - probe$t0);
    }
}
