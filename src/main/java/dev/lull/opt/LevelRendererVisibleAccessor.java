package dev.lull.opt;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** SectionListsMixin: the real visible list (the redirected field reads go through the lists). */
@Mixin(LevelRenderer.class)
public interface LevelRendererVisibleAccessor {
    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> sleep$visible();
}
