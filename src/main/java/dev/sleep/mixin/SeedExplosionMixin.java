package dev.sleep.mixin;

import dev.sleep.TestSeeds;
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

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Explosion$BlockInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)V", at = @At("RETURN"))
    private void sleep$seed(CallbackInfo ci) {
        if (TestSeeds.ON) random.setSeed(TestSeeds.next());
    }
}
