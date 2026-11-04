package dev.lull.optsupport;

import com.mojang.blaze3d.shaders.Program;

/**
 * The engine's changes to vanilla shader sources (raw source, before #moj_import is expanded): chunk multi-draw, part
 * records, materials. Applied wherever a vanilla shader is compiled - vanilla's Program.compileShaderInternal
 * (ChunkShaderPatchMixin) and Veil's recompile of every vanilla shader (VeilShaderPatchMixin), so a program Veil swaps
 * in keeps them.
 *
 * <p>1.20.1: the chunk multi-draw patches only so far; part batching's (PartBatch.patch for the entity / armor /
 * item-entity programs and SuperByteBuffer parts in the block types, Materials' vertex and fragment patches) come back
 * with part batching's port (port-pending.txt).
 */
public final class ShaderPatches {
    private ShaderPatches() {}

    public static String apply(Program.Type type, String name, String src) {
        if (type == Program.Type.VERTEX) {
            if ("rendertype_translucent".equals(name)) {
                // light volumes (LightVolumes.patchTranslucent) and the translucent multi-draw (it was never sent here before 11-03:
                // the layer kept baked light, and its sections re-meshed on every light change)
                if (Boolean.getBoolean("lab.opt.ChunkMultiDrawMixin") && ChunkMultiDraw.capable()) src = ChunkMultiDraw.patch(name, src);
            } else if (ChunkMultiDraw.SUPPORTED.contains(name)) {
                if (Boolean.getBoolean("lab.opt.ChunkMultiDrawMixin") && ChunkMultiDraw.capable()) src = ChunkMultiDraw.patch(name, src);
            }
        }
        return src;
    }
}
