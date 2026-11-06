package dev.lull.emfmixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Entity Model Features draws its parts through its own compile (EMFModelPart.renderLikeVanilla), past the call
 * PartBatchMixin takes over in ModelPart.render: its parts never reached VRAM. Its compile, as EMF 3.3 writes it (every
 * cube compiled with the pose, an IllegalStateException logged), first offers the part to PartBatch - geometry kept in
 * VRAM under the part's cube list (each variant's own list), the pose and light sent per frame. On a buffer PartBatch
 * does not batch it compiles as EMF.
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPart", remap = false)
public abstract class EmfPartGpuMixin {
    /**
     * @author sleepium (dev.lull)
     * @reason the part's geometry from VRAM (PartBatch) when its buffer is batched; EMF's own loop otherwise
     */
    @Overwrite
    public void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        List<ModelPart.Cube> cubes = ((ModelPartCubesAccessor) (Object) this).sleep$emfCubes();
        if (!cubes.isEmpty() && dev.lull.optsupport.PartBatch.capture(cubes, pose, consumer, light, overlay, color,
                (dev.lull.optsupport.PartBatch.PartCompiler) (Object) this)) return;
        try {
            for (ModelPart.Cube cube : cubes) cube.compile(pose, consumer, light, overlay, color);
        } catch (IllegalStateException e) {
            System.out.println("[EMF]: IllegalStateException caught in EMF model part");   // as EMF logs it
        }
    }
}
