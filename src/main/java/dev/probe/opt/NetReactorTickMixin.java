package dev.probe.opt;

import dev.probe.net.NetReactor;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NetReactor: the end of the server tick is a flush point (what the tick sent leaves as one task per connection). */
@Mixin(MinecraftServer.class)
public abstract class NetReactorTickMixin {
    @Inject(method = "tickServer", at = @At("RETURN"))
    private void probe$flush(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        NetReactor.onServerTick();
    }
}
