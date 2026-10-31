package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.ComponentShadow;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.componentShadow=true): ComponentShadow before each mob tick. */
@Mixin(Mob.class)
public abstract class ComponentShadowMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void probe$before(CallbackInfo ci) { ComponentShadow.before((Mob) (Object) this); }
}
