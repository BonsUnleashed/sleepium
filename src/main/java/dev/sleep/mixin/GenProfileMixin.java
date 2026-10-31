package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): the synchronous body of every ChunkStep ("step:<status>"). NOISE and BIOMES only
 * hand off here; their work is timed in GenProfileNoiseMixin, light in GenProfileLightMixin.
 */
@Mixin(ChunkStep.class)
public abstract class GenProfileMixin {
    @WrapMethod(method = "apply")
    private CompletableFuture<ChunkAccess> probe$time(WorldGenContext ctx, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                                     Operation<CompletableFuture<ChunkAccess>> original) {
        if (!GenProfile.active()) return original.call(ctx, cache, chunk);
        long t = System.nanoTime(), m = GenProfile.allocated(), pn = GenProfile.perlin();
        try {
            return original.call(ctx, cache, chunk);
        } finally {
            GenProfile.add("step:" + ((ChunkStep) (Object) this).targetStatus().getName(), System.nanoTime() - t, GenProfile.allocated() - m, GenProfile.perlin() - pn);
        }
    }
}
