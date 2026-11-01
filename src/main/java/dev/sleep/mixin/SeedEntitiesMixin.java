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
 * Exactness tests (-Dsleep.seedEntities=true): every entity's random seeded from its id instead of the clock, so a
 * scene built from the same first entity id runs the same draws (tools/exactness.py). The UUID, drawn by vanilla before
 * this seed is set, is made from the id too (from its own generator, so the entity's draws stay as they were): it shows
 * in the saved data of a vehicle's passengers.
 */
@Mixin(Entity.class)
public abstract class SeedEntitiesMixin {
    private static final boolean SEED = Boolean.getBoolean("sleep.seedEntities");

    @Shadow @Final protected RandomSource random;
    @Shadow private int id;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$seed(CallbackInfo ci) {
        if (SEED) {
            random.setSeed(id * 0x9E3779B97F4A7C15L + 12345);
            ((Entity) (Object) this).setUUID(net.minecraft.util.Mth.createInsecureUUID(RandomSource.create(id * 0xC2B2AE3D27D4EB4FL + 777)));
        }
    }
}
