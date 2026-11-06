package dev.lull.emfmixin;

import java.lang.invoke.MethodHandles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Entity Model Features compiles each model's animation into one generated static method; with Fresh Animations it is
 * over HotSpot's 8 000-byte limit before EMF 3.3 and never JIT-compiled (35 % of a frame in the interpreter on
 * 1.20.1). The generated class' bytes (compileOrNull's only byte[] local) are cut into parts the JIT compiles
 * (HugeMethods) before EMF defines the class - checked by defining and verifying the cut class first; on any doubt EMF
 * gets its own bytes. EMF 3.3+ cuts its own: nothing to do there (no method that large).
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.math.asm.ASMParser", remap = false)
public abstract class EmfAsmSplitMixin {
    @ModifyVariable(method = "compileOrNull", at = @At("STORE"), require = 1)
    private static byte[] sleep$split(byte[] bytes) {
        return dev.lull.optsupport.EmfSplit.split(bytes, MethodHandles.lookup().lookupClass().getClassLoader());
    }
}
