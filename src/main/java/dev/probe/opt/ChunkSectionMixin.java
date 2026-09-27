package dev.probe.opt;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.probe.optsupport.ChunkBufferTag;
import dev.probe.optsupport.ChunkMultiDraw;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ChunkMultiDraw: tags the section's layer buffers; a reset (origin change, release) frees its arena entries. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class ChunkSectionMixin implements ChunkMultiDraw.ChunkEntries {
    @Shadow @Final private Map<RenderType, VertexBuffer> buffers;
    @org.spongepowered.asm.mixin.Unique private final Object[] probe$md = new Object[16];

    @Override
    public Object[] probe$md() {
        return probe$md;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void probe$tag(CallbackInfo ci) {
        for (var e : buffers.entrySet()) ((ChunkBufferTag) e.getValue()).probe$tag((SectionRenderDispatcher.RenderSection) (Object) this, e.getKey());
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void probe$reset(CallbackInfo ci) {
        dev.probe.optsupport.SectionLists.changed();
        ChunkMultiDraw.release((SectionRenderDispatcher.RenderSection) (Object) this);
    }
}
