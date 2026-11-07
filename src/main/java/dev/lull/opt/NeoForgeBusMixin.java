package dev.lull.opt;

import dev.lull.optsupport.CompositeEventBus;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/bus-composite: install CompositeEventBus as NeoForge.EVENT_BUS.
 * The bus classes live in the boot layer (ModuleClassLoader) and cannot be mixed into; NeoForge itself is in the
 * game layer, so we wrap the bus at the point where NeoForge builds it.
 */
@Mixin(NeoForge.class)
public abstract class NeoForgeBusMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/BusBuilder;build()Lnet/neoforged/bus/api/IEventBus;"))
    private static IEventBus sleep$wrap(BusBuilder builder) {
        return new CompositeEventBus((net.neoforged.bus.BusBuilderImpl) builder);   // the bus itself, not a wrapper
    }
}
