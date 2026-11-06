package testmods.foreign;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stand-in for another mod's mixin (a player's server on CurseForge, 11-06): a MixinExtras wrap of the event post in
 * CommonHooks.onEntityIncomingDamage. Only applied with -Dsleep.foreignTest=true. The event-site rewrite must leave such
 * a method alone - before, the two together gave a VerifyError (operand stack underflow) when CommonHooks loaded.
 */
@Mixin(CommonHooks.class)
public abstract class ForeignPostWrapMixin {
    @WrapOperation(method = "onEntityIncomingDamage", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event foreign$post(IEventBus bus, Event event, Operation<Event> original) {
        return original.call(bus, event);
    }
}
