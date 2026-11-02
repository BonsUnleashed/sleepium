package dev.lull.shadermixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import dev.lull.shaders.PackShader;
import dev.lull.shaders.ProgramBinaryCache;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ProgramBinaryCache at a shaderpack program's link: a cached binary loaded instead of linking (its shaders were
 * not compiled: ShaderBinaryCompileMixin); refused by the driver, the shaders are compiled from the pack's sources
 * and linked here. Not cached: linked as vanilla does, retrievable, then stored. Vanilla's own programs untouched.
 */
@Mixin(ProgramManager.class)
public abstract class ShaderBinaryLinkMixin {
    @Inject(method = "linkShader", at = @At("HEAD"), cancellable = true)
    private static void sleep$link(Shader shader, CallbackInfo ci) {
        PackShader.Building b = PackShader.building;
        if (b == null || !(shader instanceof PackShader)) return;
        int id = shader.getId();
        if (!b.cached()) {
            ProgramBinaryCache.retrievable(id);   // vanilla links next, sleep$store keeps it
            return;
        }
        shader.attachToProgram();
        if (ProgramBinaryCache.load(id, b.key())) {
            ci.cancel();
            return;
        }
        // the driver refused the binary (updated?): the shaders compiled now, as vanilla would have
        int[] attached = new int[8];
        int[] count = new int[1];
        GL20.glGetAttachedShaders(id, count, attached);
        for (int i = 0; i < count[0]; i++) {
            int sh = attached[i];
            boolean vertex = GL20.glGetShaderi(sh, GL20.GL_SHADER_TYPE) == GL20.GL_VERTEX_SHADER;
            GlStateManager.glShaderSource(sh, java.util.List.of(vertex ? b.vsh() : b.fsh()));
            GlStateManager.glCompileShader(sh);
        }
        ProgramBinaryCache.retrievable(id);
        GlStateManager.glLinkProgram(id);
        if (GlStateManager.glGetProgrami(id, GL20.GL_LINK_STATUS) != 0) ProgramBinaryCache.store(id, b.key());
        else System.out.println("[sleep/shaders] link after a refused binary: " + GlStateManager.glGetProgramInfoLog(id, 32768));
        ci.cancel();
    }

    @Inject(method = "linkShader", at = @At("TAIL"))
    private static void sleep$store(Shader shader, CallbackInfo ci) {
        PackShader.Building b = PackShader.building;
        if (b == null || b.cached() || !(shader instanceof PackShader)) return;
        if (GlStateManager.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS) != 0) ProgramBinaryCache.store(shader.getId(), b.key());
    }
}
