package dev.lull.geomixin;

import dev.lull.optsupport.ParallelRender;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.core.molang.MolangParser;

/**
 * GeckoLib's Molang state outside the variables' values, inside ParallelRender jobs. 1.21's GeckoLib (4.9) keeps the
 * actor (the object being animated) in one static field, made per job here; 4.8.4 has no actor - its queries are
 * suppliers set into the variables (job-local: MolangVariableMixin) - but looks every variable up by name in one static
 * map (MolangParser.VARIABLES, a fastutil hash map, not safe for a write beside reads) and adds a name it does not know
 * yet. Lookups are made under the map's lock; a name not there yet inside a job hands the job to the render thread,
 * which adds it (from then on workers find it).
 */
@Mixin(value = MolangParser.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MolangActorMixin {
    @Redirect(method = "getVariable(Ljava/lang/String;)Lsoftware/bernie/geckolib/core/molang/LazyVariable;", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object sleep$lookup(Map map, Object name, Function make) {
        synchronized (map) {
            Object v = map.get(name);
            if (v != null) return v;
            if (ParallelRender.inJob()) throw ParallelRender.REFUSE_NOT_READY;
            v = make.apply(name);
            map.put(name, v);
            return v;
        }
    }
}
