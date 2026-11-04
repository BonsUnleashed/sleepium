package dev.lull.opt;

import java.util.List;
import java.util.RandomAccess;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * WorldgenAlloc: SurfaceRules.SequenceRule.tryApply runs per block and rule level; its for-each allocates an
 * ImmutableList iterator each time (escape analysis loses it in the recursive, megamorphic rule tree): ~16 % of
 * worldgen allocation. Indexed loop over the same list, same order.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$SequenceRule")
public abstract class WorldgenAllocSequenceMixin {
    @Shadow @Final private List<Object> rules;

    /**
     * @author probe
     * @reason indexed loop, no iterator per call
     */
    @Overwrite
    @Nullable
    public BlockState tryApply(int x, int y, int z) {
        List<Object> l = rules;
        if (l instanceof RandomAccess) {
            for (int i = 0, n = l.size(); i < n; i++) {
                BlockState s = ((SurfaceRuleInvoker) l.get(i)).sleep$tryApply(x, y, z);
                if (s != null) return s;
            }
            return null;
        }
        for (Object r : l) {
            BlockState s = ((SurfaceRuleInvoker) r).sleep$tryApply(x, y, z);
            if (s != null) return s;
        }
        return null;
    }
}
