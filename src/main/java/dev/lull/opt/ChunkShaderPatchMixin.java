package dev.lull.opt;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ChunkMultiDraw: the vanilla chunk vertex shaders get the multi-draw offset path (see ChunkMultiDraw.patch); the
 * entity shaders the residents' path. The source is patched as it is read (the local it is stored in), not in the
 * preprocessor call: Oculus / Iris redirect that call, and a redirect of ours there gave way - the patches and with them
 * every VRAM resident were off next to them (client_entities with Oculus: 241 fps, 0 resident draws).
 */
@Mixin(Program.class)
public abstract class ChunkShaderPatchMixin {
    @Unique private static Program.Type sleep$type;
    @Unique private static String sleep$name;

    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void sleep$which(Program.Type type, String name, InputStream in, String sourceName, GlslPreprocessor preprocessor,
            CallbackInfoReturnable<Integer> cir) {
        sleep$type = type;   // shaders compile on the render thread, one at a time
        sleep$name = name;
    }

    @ModifyVariable(method = "compileShaderInternal", at = @At("STORE"), index = 5)
    private static String sleep$patch(String src) {
        return src == null ? null : dev.lull.optsupport.ShaderPatches.apply(sleep$type, sleep$name, src);
    }
}
