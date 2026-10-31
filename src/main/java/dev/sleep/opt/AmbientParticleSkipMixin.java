package dev.sleep.opt;

import dev.sleep.optsupport.ClientCounters;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/ambient-skip (client): doAnimateTick asks the biome of every random position it samples (twice 667 per tick) only
 * for its ambient particle. When no loaded chunk has a biome with one (AmbientChunksMixin), every answer is "none": the
 * position's biome comes from a loaded chunk's palette or, for unloaded chunks, is plains. Then a biome without ambient
 * particle stands in, and the fuzzed zoom lookup is skipped. No random number is drawn either way (canSpawn only runs
 * when there is a particle).
 */
@Mixin(ClientLevel.class)
public abstract class AmbientParticleSkipMixin {
    @Unique private Holder<Biome> sleep$none;
    @Unique private long sleep$tick = Long.MIN_VALUE;
    @Unique private boolean sleep$any;

    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> sleep$biome(ClientLevel level, BlockPos pos) {
        long t = ClientCounters.ambientVersion;   // bumped at every chunk load, biome update and drop
        if (t != sleep$tick) {
            sleep$tick = t;
            sleep$any = ((ClientCounters.AmbientChunks) level.getChunkSource()).sleep$anyAmbient();
        }
        if (!sleep$any) {
            Holder<Biome> none = sleep$none;
            if (none == null) {
                none = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
                if (none.value().getAmbientParticle().isPresent()) none = null;   // datapack gave plains particles
                sleep$none = none;
            }
            if (none != null) {
                ClientCounters.biomeSkips++;
                return none;
            }
        }
        return level.getBiome(pos);
    }
}
