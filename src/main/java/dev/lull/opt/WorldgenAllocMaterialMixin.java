package dev.lull.opt;

import java.util.List;
import java.util.RandomAccess;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** WorldgenAlloc: MaterialRuleList.calculate (per block of NOISE) without an iterator per call (~12 % of allocation). */
@Mixin(MaterialRuleList.class)
public abstract class WorldgenAllocMaterialMixin {
    @Shadow @Final private List<NoiseChunk.BlockStateFiller> materialRuleList;

    /**
     * @author probe
     * @reason indexed loop, no iterator per call
     */
    @Overwrite
    @Nullable
    public BlockState calculate(DensityFunction.FunctionContext ctx) {
        List<NoiseChunk.BlockStateFiller> l = materialRuleList;
        if (l instanceof RandomAccess) {
            for (int i = 0, n = l.size(); i < n; i++) {
                BlockState s = l.get(i).calculate(ctx);
                if (s != null) return s;
            }
            return null;
        }
        for (NoiseChunk.BlockStateFiller f : l) {
            BlockState s = f.calculate(ctx);
            if (s != null) return s;
        }
        return null;
    }
}
