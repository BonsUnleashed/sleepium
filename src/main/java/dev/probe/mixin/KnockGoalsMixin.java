package dev.probe.mixin;

import dev.probe.Knock;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.knock=...): goal selectors switched off (Knock). */
@Mixin(GoalSelector.class)
public abstract class KnockGoalsMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void probe$tick(CallbackInfo ci) {
        if (Knock.GOALS) ci.cancel();
    }

    @Inject(method = "tickRunningGoals", at = @At("HEAD"), cancellable = true)
    private void probe$running(boolean all, CallbackInfo ci) {
        if (Knock.GOALS) ci.cancel();
    }
}
