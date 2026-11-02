package dev.lull.opt;

import com.mojang.blaze3d.vertex.MeshData;
import dev.lull.optsupport.LightVolumes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** ShaderLightMixin: a mesh built with light recipes in its normal bytes. */
@Mixin(MeshData.class)
public abstract class ShaderLightMeshMixin implements LightVolumes.RecipeMesh {
    @Unique private LightVolumes.CompileInfo sleep$recipeInfo;

    @Override
    public LightVolumes.CompileInfo sleep$recipeInfo() {
        return sleep$recipeInfo;
    }

    @Override
    public void sleep$setRecipeInfo(LightVolumes.CompileInfo info) {
        sleep$recipeInfo = info;
    }
}
