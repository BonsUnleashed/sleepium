package dev.lull.mixin;

import dev.lull.optsupport.MobQuiet;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsleep.mobQuiet=true): MobQuiet's before / after around a server mob's tick. */
@Mixin(Mob.class)
public abstract class MobQuietMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sleep$before(CallbackInfo ci) {
        Mob m = (Mob) (Object) this;
        if (!m.level().isClientSide) MobQuiet.before(m);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void sleep$after(CallbackInfo ci) {
        Mob m = (Mob) (Object) this;
        if (!m.level().isClientSide && MobQuiet.current == m) MobQuiet.after(m);
    }
}
