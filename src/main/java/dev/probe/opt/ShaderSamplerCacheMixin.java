package dev.probe.opt;

import dev.probe.optsupport.ClientCounters;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/shader-samplers (client): ShaderInstance.apply() asks the driver for every sampler's uniform location by name
 * (glGetUniformLocation, a string lookup in the driver) on every apply: a third of the render thread's native time
 * (JFR). updateLocations() already stored exactly these locations in samplerLocations, index for index with
 * samplerNames, for the same linked program; they are used instead. A name not found there asks the driver as before.
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderSamplerCacheMixin {
    @Shadow @Final private List<String> samplerNames;
    @Shadow @Final private List<Integer> samplerLocations;

    @Redirect(method = "apply", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;glGetUniformLocation(ILjava/lang/CharSequence;)I"))
    private int probe$location(int program, CharSequence name) {
        List<String> names = samplerNames;
        for (int i = 0, n = Math.min(names.size(), samplerLocations.size()); i < n; i++) {
            if (names.get(i) == name) {
                ClientCounters.samplerHits++;
                return samplerLocations.get(i);
            }
        }
        return com.mojang.blaze3d.shaders.Uniform.glGetUniformLocation(program, name);
    }
}
