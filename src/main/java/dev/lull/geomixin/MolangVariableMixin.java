package dev.lull.geomixin;

import dev.lull.optsupport.ParallelRender;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import software.bernie.geckolib.GeckoLibConstants;
import software.bernie.geckolib.loading.math.value.Variable;

/**
 * GeckoLib's Molang variables are global (query.anim_time is set by every controller before its keyframes are read):
 * inside a ParallelRender job a set goes to the job's own values and a get reads them first, then the global one
 * (what was set outside rendering). Outside jobs exactly GeckoLib's code.
 */
@Mixin(value = Variable.class, remap = false)
public abstract class MolangVariableMixin {
    @Shadow @Final private String name;
    @Shadow @Final private AtomicReference<DoubleSupplier> value;

    /**
     * @author probe
     * @reason job-local values (class comment); the rest is GeckoLib's body
     */
    @Overwrite
    public double get() {
        var locals = ParallelRender.jobLocals();
        DoubleSupplier s = locals != null ? (DoubleSupplier) locals.get(this) : null;
        try {
            return (s != null ? s : this.value.get()).getAsDouble();
        } catch (Exception ex) {
            GeckoLibConstants.LOGGER.error("Attempted to use Molang variable for incompatible animatable type (" + this.name + "). An animation json needs to be fixed", ex.getMessage());
            return 0;
        }
    }

    /**
     * @author probe
     * @reason job-local values (class comment)
     */
    @Overwrite
    public void set(final double v) {
        set(() -> v);
    }

    /**
     * @author probe
     * @reason job-local values (class comment)
     */
    @Overwrite
    public void set(final DoubleSupplier v) {
        var locals = ParallelRender.jobLocals();
        if (locals != null) locals.put(this, v);
        else this.value.set(v);
    }
}
