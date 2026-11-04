package dev.lull.geomixin;

import dev.lull.optsupport.ParallelRender;
import java.util.function.DoubleSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.core.molang.LazyVariable;

/**
 * GeckoLib's Molang variables are global (query.anim_time is set by every controller before its keyframes are read,
 * GeoModel.applyMolangQueries sets the entity's queries before each render): inside a ParallelRender job a set goes to
 * the job's own values and a get reads them first, then the global one (what was set outside rendering). Outside jobs
 * exactly GeckoLib's code.
 * <p>
 * GeckoLib 4.8.4: the variables are LazyVariable (a supplier over mclib's Variable, which it overrides whole: get and
 * both sets); expressions hold them directly, compound expressions' temporaries included.
 */
@Mixin(value = LazyVariable.class, remap = false)
public abstract class MolangVariableMixin {
    @Shadow private DoubleSupplier valueSupplier;

    /**
     * @author probe
     * @reason job-local values (class comment); the rest is GeckoLib's body
     */
    @Overwrite
    public double get() {
        var locals = ParallelRender.jobLocals();
        DoubleSupplier s = locals != null ? (DoubleSupplier) locals.get(this) : null;
        return (s != null ? s : this.valueSupplier).getAsDouble();
    }

    /**
     * @author probe
     * @reason job-local values (class comment)
     */
    @Overwrite
    public void set(double v) {
        set(() -> v);
    }

    /**
     * @author probe
     * @reason job-local values (class comment)
     */
    @Overwrite
    public void set(DoubleSupplier v) {
        var locals = ParallelRender.jobLocals();
        if (locals != null) locals.put(this, v);
        else this.valueSupplier = v;
    }
}
