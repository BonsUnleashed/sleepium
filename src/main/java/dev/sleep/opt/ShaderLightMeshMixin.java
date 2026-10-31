package dev.sleep.opt;

import com.mojang.blaze3d.vertex.MeshData;
import dev.sleep.optsupport.LightVolumes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** ShaderLightMixin: a mesh built with light recipes in its normal bytes. */
@Mixin(MeshData.class)
public abstract class ShaderLightMeshMixin implements LightVolumes.RecipeMesh {
    @Unique private LightVolumes.CompileInfo probe$recipeInfo;

    @Override
    public LightVolumes.CompileInfo probe$recipeInfo() {
        return probe$recipeInfo;
    }

    @Override
    public void probe$setRecipeInfo(LightVolumes.CompileInfo info) {
        probe$recipeInfo = info;
    }
}
