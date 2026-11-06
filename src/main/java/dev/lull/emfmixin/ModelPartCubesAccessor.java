package dev.lull.emfmixin;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** a part's current cube list (Entity Model Features swaps it per model variant): the key of its geometry in VRAM */
@Mixin(ModelPart.class)
public interface ModelPartCubesAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> sleep$emfCubes();
}
