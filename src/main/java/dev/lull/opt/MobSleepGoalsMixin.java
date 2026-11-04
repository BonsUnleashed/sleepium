package dev.lull.opt;

import dev.lull.optsupport.sleep.MobSleepHooks;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob sleep: a modification counter on GoalSelector - goals added or removed change a sleeping mob's contexts. */
@Mixin(GoalSelector.class)
public abstract class MobSleepGoalsMixin implements MobSleepHooks.GoalEpoch {
    @Unique private int sleep$goalEpoch;

    @Override
    public int sleep$goalEpoch() {
        return sleep$goalEpoch;
    }

    @Inject(method = {"addGoal", "removeGoal", "removeAllGoals"}, at = @At("HEAD"))
    private void sleep$changed(CallbackInfo ci) {
        sleep$goalEpoch++;
    }
}
