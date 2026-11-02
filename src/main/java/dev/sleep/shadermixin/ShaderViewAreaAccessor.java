package dev.sleep.shadermixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The shadow pass walks every built section (not only the camera's visible ones). */
@Mixin(LevelRenderer.class)
public interface ShaderViewAreaAccessor {
    @Accessor("viewArea")
    ViewArea sleep$viewArea();
}
