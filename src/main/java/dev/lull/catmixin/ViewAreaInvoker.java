package dev.lull.catmixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** KineticResident: the render section of a global block entity (not in the section loop). */
@Mixin(ViewArea.class)
public interface ViewAreaInvoker {
    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection sleep$sectionAt(BlockPos pos);
}
