package dev.lull.opt;

import dev.lull.optsupport.LazyBiome;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** WorldgenAlloc: updateY without a lambda and a memoizing supplier per block (see LazyBiome). */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class WorldgenAllocSurfaceContextMixin {
    @Shadow long lastUpdateY;
    @Shadow Supplier<Holder<Biome>> biome;
    @Shadow int blockY;
    @Shadow int waterHeight;
    @Shadow int stoneDepthBelow;
    @Shadow int stoneDepthAbove;
    @Shadow @Final BlockPos.MutableBlockPos pos;
    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;
    @Unique private LazyBiome sleep$biome;

    /**
     * @author probe
     * @reason same lazy biome lookup, one reusable supplier per context instead of two objects per block
     */
    @Overwrite
    protected void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ) {
        this.lastUpdateY++;
        LazyBiome b = sleep$biome;
        if (b == null) sleep$biome = b = new LazyBiome(biomeGetter, pos);
        this.biome = b.reset(blockX, blockY, blockZ);
        this.blockY = blockY;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
    }
}
