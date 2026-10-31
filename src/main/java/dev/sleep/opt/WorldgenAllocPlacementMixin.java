package dev.sleep.opt;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * WorldgenAlloc (placement): PlacedFeature.placeWithContext chains one flatMap per placement modifier over
 * Stream.of(origin) — a pipeline of stream objects per placed feature per chunk. A sequential flatMap calls the mapper
 * for an element and pushes each element of the returned stream downstream before taking the next one (depth first),
 * closing the returned stream afterwards. The same traversal written as recursion: the same getPositions calls, the
 * same random draws, the same place calls, in the same order; each modifier's stream is still consumed and closed.
 */
@Mixin(PlacedFeature.class)
public abstract class WorldgenAllocPlacementMixin {
    @Shadow @Final private Holder<ConfiguredFeature<?, ?>> feature;
    @Shadow @Final private List<PlacementModifier> placement;

    /**
     * @author probe
     * @reason flatMap chain replaced by the equivalent depth-first recursion
     */
    @Overwrite
    private boolean placeWithContext(PlacementContext context, RandomSource source, BlockPos pos) {
        ConfiguredFeature<?, ?> configuredfeature = this.feature.value();
        boolean[] placed = new boolean[1];
        probe$apply(0, pos, context, source, configuredfeature, placed);
        return placed[0];
    }

    @Unique
    private void probe$apply(int i, BlockPos pos, PlacementContext context, RandomSource source, ConfiguredFeature<?, ?> feature, boolean[] placed) {
        if (i == this.placement.size()) {
            if (feature.place(context.getLevel(), context.generator(), source, pos)) placed[0] = true;
            return;
        }
        try (Stream<BlockPos> s = this.placement.get(i).getPositions(context, source, pos)) {
            if (s != null) s.sequential().forEach(p -> probe$apply(i + 1, p, context, source, feature, placed));
        }
    }
}
