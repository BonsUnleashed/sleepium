package dev.sleep.catmixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import dev.sleep.optsupport.KineticMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** KineticMemo: a kinetic block entity's overstress tint state, part of what its memo is valid for. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBeMixin implements KineticMemo.Effects {
    @Shadow
    protected KineticEffectHandler effects;

    @Override
    public float sleep$overStressed() {
        return effects == null ? 0f : ((OverStressAccessor) effects).sleep$overStressed();
    }
}
