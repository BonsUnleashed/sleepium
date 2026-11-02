package dev.lull.shadermixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import dev.lull.shaders.PackShader;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ProgramBinaryCache: a shaderpack program whose linked binary is cached gets its shader objects without a compile
 * (ShaderBinaryLinkMixin loads the binary in place of the link, and compiles them only if the driver refuses it).
 * Every other shader compiles as in vanilla.
 */
@Mixin(Program.class)
public abstract class ShaderBinaryCompileMixin {
    @Inject(method = "compileShaderInternal", at = @At("HEAD"), cancellable = true)
    private static void sleep$cached(Program.Type type, String name, InputStream data, String source, GlslPreprocessor pre,
            CallbackInfoReturnable<Integer> cir) {
        PackShader.Building b = PackShader.building;
        if (b != null && b.cached()) cir.setReturnValue(GlStateManager.glCreateShader(type == Program.Type.VERTEX ? 35633 : 35632));   // (getGlType is package-private)
    }
}
