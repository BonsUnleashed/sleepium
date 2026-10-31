package dev.sleep.geomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.sleep.optsupport.MolangJit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.loading.math.MathValue;

/** Every Molang expression GeckoLib compiles (animation keyframes, easing arguments...): compiled to bytecode by MolangJit. */
@Mixin(value = MathParser.class, remap = false)
public abstract class MolangJitMixin {
    @ModifyReturnValue(method = "compileMolang", at = @At("RETURN"))
    private static MathValue probe$jit(MathValue parsed) {
        return MolangJit.compile(parsed);
    }
}
