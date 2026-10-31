package dev.sleep.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exactness tests (-Dprobe.seedEntities=true): every entity's random seeded from its id instead of the clock, so a
 * scene built from the same first entity id runs the same draws (tools/ent_hash.sh with mobs).
 */
@Mixin(Entity.class)
public abstract class SeedEntitiesMixin {
    private static final boolean PROBE$SEED = Boolean.getBoolean("probe.seedEntities");

    @Shadow @Final protected RandomSource random;
    @Shadow private int id;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void probe$seed(CallbackInfo ci) {
        if (PROBE$SEED) random.setSeed(id * 0x9E3779B97F4A7C15L + 12345);
    }
}
