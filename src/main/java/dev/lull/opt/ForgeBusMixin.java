package dev.lull.opt;

import dev.lull.optsupport.CompositeEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/bus-composite: install CompositeEventBus as MinecraftForge.EVENT_BUS (1.21.1: NeoForgeBusMixin).
 * The bus classes live in the boot layer and cannot be mixed into; Forge itself is in the game layer, so we wrap the
 * bus at the point where MinecraftForge builds it.
 */
@Mixin(value = MinecraftForge.class, remap = false)
public abstract class ForgeBusMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/BusBuilder;build()Lnet/minecraftforge/eventbus/api/IEventBus;"))
    private static IEventBus sleep$wrap(BusBuilder builder) {
        return new CompositeEventBus(builder.build());
    }
}
