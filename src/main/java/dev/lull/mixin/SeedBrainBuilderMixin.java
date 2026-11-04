package dev.lull.mixin;

import dev.lull.TestSeeds;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.common.util.BrainBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exactness tests (-Dsleep.seedEntities): Forge 1.20.1 rebuilds every brain in LivingEntity's constructor through a
 * BrainBuilder (ForgeHooks.onLivingMakeBrain, LivingMakeBrainEvent), which keeps sensor and memory types in HashSets
 * and behaviours per priority in HashMaps - identity-hash order, per run - so the sensors are made (each drawing its
 * first scan time from Sensor.RANDOM) and behaviours started in a per-run order. With the test seeds the builder keeps
 * insertion order (SeedBrainOrderMixin does the same in Brain); without them it is Forge's own.
 */
@Mixin(value = BrainBuilder.class, remap = false)
public abstract class SeedBrainBuilderMixin {
    @Shadow @Final @Mutable private Collection<Object> memoryTypes;
    @Shadow @Final @Mutable private Collection<Object> sensorTypes;
    @Shadow @Final private Map<Integer, Map<Object, Set<Object>>> availableBehaviorsByPriority;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$ordered(CallbackInfo ci) {
        if (TestSeeds.ON) {
            memoryTypes = new LinkedHashSet<>();
            sensorTypes = new LinkedHashSet<>();
        }
    }

    private static void sleep$add(Map<Integer, Map<Object, Set<Object>>> into, Integer priority, Object activity, Collection<Object> behaviours) {
        into.computeIfAbsent(priority, p -> new LinkedHashMap<>()).computeIfAbsent(activity, a -> new LinkedHashSet<>()).addAll(behaviours);
    }

    @Inject(method = "addBehaviorToActivityByPriority", at = @At("HEAD"), cancellable = true)
    private void sleep$addOne(Integer priority, net.minecraft.world.entity.schedule.Activity activity, net.minecraft.world.entity.ai.behavior.BehaviorControl<?> behaviour, CallbackInfo ci) {
        if (!TestSeeds.ON) return;
        sleep$add(availableBehaviorsByPriority, priority, activity, java.util.List.of(behaviour));
        ci.cancel();
    }

    @Inject(method = "addAvailableBehaviorsByPriorityFrom", at = @At("HEAD"), cancellable = true)
    private void sleep$from(Map<Integer, Map<Object, Set<Object>>> from, CallbackInfo ci) {
        if (!TestSeeds.ON) return;
        from.forEach((p, byActivity) -> byActivity.forEach((a, set) -> sleep$add(availableBehaviorsByPriority, p, a, set)));
        ci.cancel();
    }

    @Inject(method = "addAvailableBehaviorsByPriorityTo", at = @At("HEAD"), cancellable = true)
    private void sleep$to(Map<Integer, Map<Object, Set<Object>>> to, CallbackInfo ci) {
        if (!TestSeeds.ON) return;
        availableBehaviorsByPriority.forEach((p, byActivity) -> byActivity.forEach((a, set) -> sleep$add(to, p, a, set)));
        ci.cancel();
    }
}
