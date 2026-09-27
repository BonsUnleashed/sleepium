package dev.probe.opt;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/frame-garbage (client): the overworld's brightness-dependent fog colour (a multiply per fetched colour, 216 per
 * frame) remembered per input colour instance and brightness: with Vec3FromRgbMixin the inputs are the same few
 * instances, and the function is pure (a product of the same doubles).
 */
@Mixin(targets = "net.minecraft.client.renderer.DimensionSpecialEffects$OverworldEffects")
public abstract class OverworldFogMixin {
    @Unique
    private record Entry(Vec3 in, int brightness, Vec3 out) {}

    @Unique private static final Entry[] probe$fog = new Entry[64];

    @Inject(method = "getBrightnessDependentFogColor", at = @At("HEAD"), cancellable = true)
    private void probe$hit(Vec3 color, float brightness, CallbackInfoReturnable<Vec3> cir) {
        int b = Float.floatToRawIntBits(brightness);
        Entry e = probe$fog[(System.identityHashCode(color) ^ b * 31) & 63];
        if (e != null && e.in() == color && e.brightness() == b) cir.setReturnValue(e.out());
    }

    @Inject(method = "getBrightnessDependentFogColor", at = @At("RETURN"))
    private void probe$keep(Vec3 color, float brightness, CallbackInfoReturnable<Vec3> cir) {
        int b = Float.floatToRawIntBits(brightness);
        probe$fog[(System.identityHashCode(color) ^ b * 31) & 63] = new Entry(color, b, cir.getReturnValue());
    }
}
