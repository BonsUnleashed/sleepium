package dev.probe.opt;

import dev.probe.optsupport.MapAllMemo;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** WorldgenShare (mapAll): a spline coordinate's function is mapped once per NoiseChunk traversal (see MapAllMemo). */
@Mixin(DensityFunctions.Spline.Coordinate.class)
public abstract class WorldgenShareCoordinateMixin {
    @Shadow @Final private Holder<DensityFunction> function;

    /**
     * @author probe
     * @reason reuse the mapped coordinate function within one NoiseChunk traversal
     */
    @Overwrite
    public DensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
        return new DensityFunctions.Spline.Coordinate(new Holder.Direct<>(MapAllMemo.map(this.function.value(), visitor)));
    }
}
