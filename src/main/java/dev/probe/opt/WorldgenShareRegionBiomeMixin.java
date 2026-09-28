package dev.probe.opt;

import dev.probe.optsupport.BiomeConfine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WorldgenShare (biomes): a WorldGenRegion's BiomeManager serves one step on one thread: it may memoise. */
@Mixin(WorldGenRegion.class)
public abstract class WorldgenShareRegionBiomeMixin {
    @Shadow @Final private BiomeManager biomeManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void probe$confine(CallbackInfo ci) {
        ((BiomeConfine) this.biomeManager).probe$confine();
    }
}
