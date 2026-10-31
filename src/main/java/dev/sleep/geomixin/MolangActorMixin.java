package dev.sleep.geomixin;

import dev.sleep.optsupport.GeoParallel;
import dev.sleep.optsupport.ParallelRender;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.loading.math.MolangQueries;

/** GeckoLib's Molang actor (the object being animated, one static field): per job inside ParallelRender jobs. */
@Mixin(value = MolangQueries.class, remap = false)
public abstract class MolangActorMixin {
    @Shadow private static MolangQueries.Actor<?> ACTOR;

    @Redirect(method = {"updateActor", "clearActor"}, at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lsoftware/bernie/geckolib/loading/math/MolangQueries;ACTOR:Lsoftware/bernie/geckolib/loading/math/MolangQueries$Actor;"))
    private static void probe$put(MolangQueries.Actor<?> actor) {
        var locals = ParallelRender.jobLocals();
        if (locals != null) locals.put(GeoParallel.ACTOR_KEY, actor);
        else ACTOR = actor;
    }

    @Redirect(method = "getActor", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
            target = "Lsoftware/bernie/geckolib/loading/math/MolangQueries;ACTOR:Lsoftware/bernie/geckolib/loading/math/MolangQueries$Actor;"))
    private static MolangQueries.Actor<?> probe$get() {
        var locals = ParallelRender.jobLocals();
        return locals != null ? (MolangQueries.Actor<?>) locals.get(GeoParallel.ACTOR_KEY) : ACTOR;
    }
}
