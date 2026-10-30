package dev.probe.opt;

import dev.probe.optsupport.sleep.MobSleepHooks;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob sleep: a modification counter on GoalSelector - goals added or removed change a sleeping mob's contexts. */
@Mixin(GoalSelector.class)
public abstract class MobSleepGoalsMixin implements MobSleepHooks.GoalEpoch {
    @Unique private int probe$goalEpoch;

    @Override
    public int probe$goalEpoch() {
        return probe$goalEpoch;
    }

    @Inject(method = {"addGoal", "removeGoal", "removeAllGoals"}, at = @At("HEAD"))
    private void probe$changed(CallbackInfo ci) {
        probe$goalEpoch++;
    }
}
