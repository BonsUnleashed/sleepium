package dev.lull.catmixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.lull.optsupport.KineticMemo;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** KineticMemo: the turn every rotating Create part gets (axis, angle) while a draw is being recorded. */
@Mixin(value = KineticBlockEntityRenderer.class, remap = false)
public abstract class KineticRotationMixin {
    @Inject(method = "kineticRotationTransform", at = @At("HEAD"))
    private static void sleep$turn(SuperByteBuffer buffer, KineticBlockEntity be, Direction.Axis axis, float angle, int light,
            CallbackInfoReturnable<SuperByteBuffer> cir) {
        KineticMemo.rotating(buffer, be, axis, angle);
    }
}
