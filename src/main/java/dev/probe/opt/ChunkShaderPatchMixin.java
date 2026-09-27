package dev.probe.opt;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import dev.probe.optsupport.ChunkMultiDraw;
import java.io.InputStream;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** ChunkMultiDraw: the vanilla chunk vertex shaders get the multi-draw offset path (see ChunkMultiDraw.patch). */
@Mixin(Program.class)
public abstract class ChunkShaderPatchMixin {
    @Redirect(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;process(Ljava/lang/String;)Ljava/util/List;"))
    private static List<String> probe$patch(GlslPreprocessor pp, String src, Program.Type type, String name, InputStream in, String sourceName,
            GlslPreprocessor preprocessor) {
        if (type == Program.Type.VERTEX) {
            if (ChunkMultiDraw.SUPPORTED.contains(name)) {
                if (Boolean.getBoolean("lab.opt.ChunkMultiDrawMixin") && ChunkMultiDraw.capable()) src = ChunkMultiDraw.patch(name, src);
            } else if (Boolean.getBoolean("lab.opt.PartBatchMixin") && (name.startsWith("rendertype_entity") || name.startsWith("rendertype_armor"))
                    && dev.probe.optsupport.PartBatch.capable()) {
                src = dev.probe.optsupport.PartBatch.patch(name, src);
            }
        }
        return pp.process(src);
    }
}
