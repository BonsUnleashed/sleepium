package dev.sleep.mixin;

import dev.sleep.optsupport.MobQuiet;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dsleep.mobQuiet=true): random draws during a mob's tick, on its own random or another. */
@Mixin(LegacyRandomSource.class)
public abstract class MobQuietRandomMixin {
    @Inject(method = "next", at = @At("HEAD"))
    private void sleep$draw(int bits, CallbackInfoReturnable<Integer> cir) {
        var m = MobQuiet.current;
        if (m == null || Thread.currentThread() != m.level().getServer().getRunningThread()) return;
        if ((Object) this == m.getRandom()) {
            MobQuiet.own++;
            MobQuiet.site();
        }
        else MobQuiet.shared++;
    }
}
