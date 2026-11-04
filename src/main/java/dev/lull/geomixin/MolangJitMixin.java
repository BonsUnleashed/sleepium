package dev.lull.geomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.lull.optsupport.MolangJit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.core.molang.expressions.MolangValue;

/**
 * Every Molang expression GeckoLib parses (animation keyframes; parseJson's strings come here too, its numbers stay
 * plain constants): compiled to bytecode by MolangJit. 1.20.1 / GeckoLib 4.8.4: MolangParser.parseExpression (1.21.1 /
 * GeckoLib 4.9: MathParser.compileMolang), once the whole statement list is parsed.
 */
@Mixin(value = MolangParser.class, remap = false)
public abstract class MolangJitMixin {
    @ModifyReturnValue(method = "parseExpression", at = @At("RETURN"))
    private static MolangValue sleep$jit(MolangValue parsed) {
        return MolangJit.compile(parsed);
    }
}
