package dev.probe.opt;

import com.mojang.blaze3d.vertex.MeshData;
import dev.probe.optsupport.LightVolumes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** ShaderLightMixin: a mesh built with light recipes in its normal bytes. */
@Mixin(MeshData.class)
public abstract class ShaderLightMeshMixin implements LightVolumes.RecipeMesh {
    @Unique private boolean probe$recipes;

    @Override
    public boolean probe$recipes() {
        return probe$recipes;
    }

    @Override
    public void probe$setRecipes(boolean v) {
        probe$recipes = v;
    }
}
