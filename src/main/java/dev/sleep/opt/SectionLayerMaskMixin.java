package dev.sleep.opt;

import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * exp/section-layers (client): {@code CompiledSection.isEmpty(layer)} is asked for every visible section and every chunk
 * layer each frame; vanilla searches an ObjectArraySet (a second object and its array per section). The set is complete
 * before the compiled section is published (filled by the build task, then {@code compiled.set}), so its layers become a
 * bit mask on first use. Layers without a chunk layer id take vanilla's path.
 */
@Mixin(SectionRenderDispatcher.CompiledSection.class)
public abstract class SectionLayerMaskMixin {
    @Shadow @Final Set<RenderType> hasBlocks;
    @Unique private int probe$mask = -1;

    /**
     * @author probe (lab.opt.SectionLayerMaskMixin)
     * @reason bit mask instead of a set search
     */
    @Overwrite
    public boolean isEmpty(RenderType renderType) {
        int id = renderType.getChunkLayerId();
        if (id < 0 || id > 30) return !hasBlocks.contains(renderType);
        int m = probe$mask;
        if (m < 0) {
            m = 0;
            for (RenderType t : hasBlocks) {
                int k = t.getChunkLayerId();
                if (k >= 0 && k <= 30) m |= 1 << k;
            }
            probe$mask = m;
        }
        return (m & (1 << id)) == 0;
    }
}
