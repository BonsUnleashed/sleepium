package dev.sleep.mixin;

import dev.sleep.TestSeeds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exactness tests (-Dsleep.seedEntities=true): a ShufflingList's random (a brain's behaviour order) from TestSeeds. */
@Mixin(ShufflingList.class)
public abstract class SeedShufflingMixin {
    @Shadow @Final private RandomSource random;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void sleep$seed(CallbackInfo ci) {
        if (TestSeeds.ON) random.setSeed(TestSeeds.next());
    }
}
