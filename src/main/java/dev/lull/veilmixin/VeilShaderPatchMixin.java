package dev.lull.veilmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import dev.lull.optsupport.ShaderPatches;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Veil recompiles every vanilla shader after a resource reload (its dynamic buffers) and swaps the new program in, past
 * vanilla's Program.compileShaderInternal where our patches are applied: the same patches on its sources here, before
 * its own processing, so the swapped-in programs keep multi-draw, part records and materials.
 */
@Mixin(targets = "foundry.veil.impl.client.render.dynamicbuffer.VanillaShaderCompiler", remap = false)
public abstract class VeilShaderPatchMixin {
    @WrapOperation(method = "compileShader", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;process(Ljava/lang/String;)Ljava/util/List;"))
    private List<String> sleep$patch(GlslPreprocessor pp, String source, Operation<List<String>> original, @Local(argsOnly = true) ShaderInstance shader,
            @Local ResourceLocation path) {
        Program.Type type = path.getPath().endsWith(".vsh") ? Program.Type.VERTEX : path.getPath().endsWith(".fsh") ? Program.Type.FRAGMENT : null;
        if (type != null) source = ShaderPatches.apply(type, shader.getName(), source);
        return original.call(pp, source);
    }

    private static boolean ours(String src) {
        return src.contains("minecraft_sample_lightmap(Sampler2, probe_uv2)") || src.contains("minecraft_sample_lightmap(Sampler2, probeUV2(");
    }

    /**
     * Veil's final source of each recompiled vanilla shader: checked for our light line, and with -Dsleep.veil.dump=dir
     * written there (diagnostics).
     */
    @WrapOperation(method = "compileShader", at = @At(value = "INVOKE",
            target = "Lfoundry/veil/impl/client/render/shader/processor/VanillaShaderProcessor;modify(Ljava/util/Map;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;IILjava/lang/String;Lorg/lwjgl/opengl/GLCapabilities;)Ljava/lang/String;"),
            require = 0)
    private String sleep$processed(java.util.Map<String, Object> data, String shaderInstance, ResourceLocation name, com.mojang.blaze3d.vertex.VertexFormat format,
            int buffers, int type, String source, org.lwjgl.opengl.GLCapabilities caps, Operation<String> original) {
        String out = original.call(data, shaderInstance, name, format, buffers, type, source, caps);
        // our light line (shader light's recipes) in, but not out: another processor rewrote it (Sable replaces every
        // "x = y * minecraft_sample_lightmap(...)" line) - light changes must re-mesh again
        if (out != null && source != null && ours(source) && !ours(out)) dev.lull.optsupport.LightVolumes.recipesIgnored(shaderInstance + " after Veil's processing");
        String dir = System.getProperty("sleep.veil.dump");
        if (dir != null && out != null) {
            try {
                java.nio.file.Path p = java.nio.file.Path.of(dir, shaderInstance + "_" + name.getPath().replace('/', '_'));
                java.nio.file.Files.createDirectories(p.getParent());
                java.nio.file.Files.writeString(p, out);
            } catch (Exception e) {
                System.out.println("[sleep/opt] veil dump failed: " + e);
            }
        }
        return out;
    }
}
