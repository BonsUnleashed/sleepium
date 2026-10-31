package dev.sleep.opt;

import dev.sleep.optsupport.SurfaceColumns;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** WorldgenShare: preliminary surface levels shared across chunks while the Blender is empty (see SurfaceColumns). */
@Mixin(NoiseChunk.class)
public abstract class WorldgenShareSurfaceMixin {
    @Shadow @Final private Long2IntMap preliminarySurfaceLevel;

    @Shadow
    private int computePreliminarySurfaceLevel(long packedChunkPos) {
        throw new AssertionError();
    }

    @Unique private SurfaceColumns sleep$columns;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$table(int cellCountXZ, RandomState random, int firstNoiseX, int firstNoiseZ, NoiseSettings noiseSettings,
                             DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings noiseGeneratorSettings,
                             Aquifer.FluidPicker fluidPicker, Blender blendifier, CallbackInfo ci) {
        if (blendifier == Blender.empty())
            sleep$columns = SurfaceColumns.forState(random, noiseSettings.minY(), noiseSettings.height(), noiseSettings.getCellHeight());
    }

    /**
     * @author probe
     * @reason the same pure column value, shared across chunks (vanilla computes each column for every chunk near it)
     */
    @Overwrite
    public int preliminarySurfaceLevel(int x, int z) {
        int i = QuartPos.toBlock(QuartPos.fromBlock(x));
        int j = QuartPos.toBlock(QuartPos.fromBlock(z));
        SurfaceColumns t = sleep$columns;
        if (t != null) {
            int v = t.get(i, j);
            if (v != SurfaceColumns.MISS) return v;
        }
        int r = this.preliminarySurfaceLevel.computeIfAbsent(ColumnPos.asLong(i, j), this::computePreliminarySurfaceLevel);
        if (t != null) t.put(i, j, r);
        return r;
    }
}
