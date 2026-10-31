package dev.sleep.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exactness tests (-Dsleep.seedEntities=true): each level's shared random seeded from its dimension, not the clock. */
@Mixin(Level.class)
public abstract class SeedLevelMixin {
    private static final boolean PROBE$SEED = Boolean.getBoolean("sleep.seedEntities");

    @Shadow @Final public RandomSource random;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$seed(CallbackInfo ci) {
        if (PROBE$SEED) random.setSeed(((Level) (Object) this).dimension().location().toString().hashCode() * 31L + 777);
    }
}
