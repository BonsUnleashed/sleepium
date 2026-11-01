package dev.sleep.catmixin;

import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** KineticMemo: the overstress tint's state (the renderer colours parts with it). */
@Mixin(value = KineticEffectHandler.class, remap = false)
public interface OverStressAccessor {
    @Accessor("overStressedEffect")
    float sleep$overStressed();
}
