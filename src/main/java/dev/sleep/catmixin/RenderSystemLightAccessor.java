package dev.sleep.catmixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The level's light directions RenderSystem keeps (catnip's SuperByteBuffer reads the same for its diffuse). */
@Mixin(value = RenderSystem.class, remap = false)
public interface RenderSystemLightAccessor {
    @Accessor("shaderLightDirections")
    static Vector3f[] sleep$lightDirections() {
        throw new AssertionError();
    }
}
