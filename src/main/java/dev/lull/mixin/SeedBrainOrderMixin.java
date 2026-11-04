package dev.lull.mixin;

import dev.lull.TestSeeds;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Exactness tests (-Dsleep.seedEntities): a brain's behaviours of one priority are kept per activity in a HashMap keyed
 * by Activity, whose hash is the identity hash: per run on 1.20.1 / Forge (the thread that first asks differs with the
 * parallel mod loading), so the order behaviours start in - and the randoms they draw - differ from run to run. With
 * the test seeds the per-priority maps keep insertion order instead (both runs of a test alike; BrainFlat walks the same
 * maps, whatever their order).
 */
@Mixin(Brain.class)
public abstract class SeedBrainOrderMixin {
    @Redirect(method = "addActivityAndRemoveMemoriesWhenStopped", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 0))
    private Object sleep$ordered(Map<Object, Object> byPriority, Object priority, Function<Object, Object> make) {
        return byPriority.computeIfAbsent(priority, TestSeeds.ON ? k -> new java.util.LinkedHashMap<>() : make);
    }
}
