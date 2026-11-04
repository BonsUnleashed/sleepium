package dev.lull.mixin;

import dev.lull.TestSeeds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exactness tests (-Dsleep.seedEntities=true): an explosion's random from TestSeeds. */
@Mixin(Explosion.class)
public abstract class SeedExplosionMixin {
    @Shadow @Final private RandomSource random;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void sleep$seed(CallbackInfo ci) {
        if (TestSeeds.ON) random.setSeed(TestSeeds.next());
    }
}
