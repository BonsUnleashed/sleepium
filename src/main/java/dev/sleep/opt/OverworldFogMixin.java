package dev.sleep.opt;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/frame-garbage (client): the overworld's brightness-dependent fog colour (a multiply per fetched colour, 216 per
 * frame) remembered per input colour instance and brightness: with Vec3FromRgbMixin the inputs are the same few
 * instances, and the function is pure (the same product of the same doubles). An overwrite, so no callback object per
 * call.
 */
@Mixin(targets = "net.minecraft.client.renderer.DimensionSpecialEffects$OverworldEffects")
public abstract class OverworldFogMixin {
    @Unique private static final dev.sleep.optsupport.MixinHelpers.FogEntry[] probe$fog = new dev.sleep.optsupport.MixinHelpers.FogEntry[64];

    /**
     * @author probe
     * @reason vanilla's product, remembered per input instance and brightness (see the class comment)
     */
    @Overwrite
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        int b = Float.floatToRawIntBits(brightness);
        int i = (System.identityHashCode(color) ^ b * 31) & 63;
        var e = probe$fog[i];
        if (e != null && e.in() == color && e.brightness() == b) return e.out();
        Vec3 out = color.multiply((double) (brightness * 0.94F + 0.06F), (double) (brightness * 0.94F + 0.06F), (double) (brightness * 0.91F + 0.09F));
        probe$fog[i] = new dev.sleep.optsupport.MixinHelpers.FogEntry(color, b, out);
        return out;
    }
}
