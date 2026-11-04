package dev.lull.opt;

import dev.lull.optsupport.MapAllMemo;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** WorldgenShare (mapAll): a holder's function is mapped once per NoiseChunk traversal (see MapAllMemo). */
@Mixin(DensityFunctions.HolderHolder.class)
public abstract class WorldgenShareHolderMixin {
    @Shadow @Final private Holder<DensityFunction> function;

    /**
     * @author probe
     * @reason reuse the mapped referenced function within one NoiseChunk traversal
     */
    @Overwrite
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new DensityFunctions.HolderHolder(new Holder.Direct<>(MapAllMemo.map(this.function.value(), visitor))));
    }
}
