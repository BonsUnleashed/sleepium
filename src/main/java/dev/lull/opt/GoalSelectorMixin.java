package dev.lull.opt;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/goal-selector-noalloc.
 *
 * <p>Vanilla {@code GoalSelector.tick()} runs, for every mob and both selectors, every tick:
 * {@code lockedFlags.entrySet().removeIf(e -> !e.getValue().isRunning())}. On an EnumMap that allocates an
 * EntryIterator plus one Entry object per {@code next()}. JFR: ~20 % of all server-thread allocation in a
 * 350-mob scene.
 *
 * <p>Replacement: walk the 4 flags by ordinal with get/remove. Same result (removal order is irrelevant for a map
 * keyed by flag), zero allocation.
 */
@Mixin(GoalSelector.class)
public abstract class GoalSelectorMixin {
    @Unique
    private static final Goal.Flag[] sleep$FLAGS = Goal.Flag.values();

    @Shadow
    @Final
    private Map<Goal.Flag, WrappedGoal> lockedFlags;

    // 1.20.1's tick removes the stopped goals with an explicit iterator over lockedFlags.entrySet() (1.21:
    // entrySet().removeIf): the entrySet() call is redirected to do the same removals in the EnumMap's order (flag
    // ordinal) and to hand the loop an empty set, whose iterator is a shared constant
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0))
    private Set<?> sleep$removeStoppedWithoutIterator(java.util.Map<?, ?> map) {
        for (Goal.Flag flag : sleep$FLAGS) {
            WrappedGoal goal = lockedFlags.get(flag);
            if (goal != null && !goal.isRunning()) lockedFlags.remove(flag);
        }
        return java.util.Collections.emptySet();
    }
}
