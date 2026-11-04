package dev.lull.shadermixin;

import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's loaded shaders: a pack's programs are prepared for each of them before they are first drawn. */
@Mixin(GameRenderer.class)
public interface ShaderGameRendererShadersAccessor {
    @Accessor("shaders")
    Map<String, ShaderInstance> sleep$shaders();
}
