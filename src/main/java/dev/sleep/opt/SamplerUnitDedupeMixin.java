package dev.sleep.opt;

import com.mojang.blaze3d.shaders.Uniform;
import dev.sleep.optsupport.ClientCounters;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/uniform-dedupe (client): ShaderInstance.apply sets each sampler uniform to its texture unit on every apply; the
 * unit of a sampler never changes for a program, so after the first time the call is skipped (per program instance,
 * per location, forgotten when the instance's program changes).
 */
@Mixin(ShaderInstance.class)
public abstract class SamplerUnitDedupeMixin {
    @Unique private final Int2IntOpenHashMap sleep$units = newMap();
    @Unique private int sleep$program = Integer.MIN_VALUE;

    @Unique
    private static Int2IntOpenHashMap newMap() {
        Int2IntOpenHashMap m = new Int2IntOpenHashMap();
        m.defaultReturnValue(Integer.MIN_VALUE);
        return m;
    }

    @Redirect(method = "apply", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;uploadInteger(II)V"))
    private void sleep$unit(int location, int value) {
        int program = ((ShaderInstance) (Object) this).getId();
        if (program != sleep$program) {   // another program under this instance (Veil's recompile): its units are unknown
            sleep$units.clear();
            sleep$program = program;
        }
        if (location != -1 && sleep$units.get(location) == value) {
            ClientCounters.uniformsSkipped++;
            return;
        }
        Uniform.uploadInteger(location, value);
        sleep$units.put(location, value);
        ClientCounters.uniformsSent++;
    }
}
