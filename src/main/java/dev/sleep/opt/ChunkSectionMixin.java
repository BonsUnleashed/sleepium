package dev.sleep.opt;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.sleep.optsupport.ChunkBufferTag;
import dev.sleep.optsupport.ChunkMultiDraw;
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
    @org.spongepowered.asm.mixin.Unique private final Object[] sleep$md = new Object[16];
    @org.spongepowered.asm.mixin.Unique private int sleep$visIdx = -1, sleep$visBuild = -1, sleep$dirtyBuild = -1, sleep$epoch;

    @Override
    public int sleep$epoch() {
        return sleep$epoch;
    }

    @Override
    public Object[] sleep$md() {
        return sleep$md;
    }

    @Override
    public int sleep$visIdx(int b) {
        return sleep$visBuild == b ? sleep$visIdx : -1;
    }

    @Override
    public void sleep$setVis(int idx, int b) {
        sleep$visIdx = idx;
        sleep$visBuild = b;
    }

    @Override
    public int sleep$dirtyBuild() {
        return sleep$dirtyBuild;
    }

    @Override
    public void sleep$setDirtyBuild(int b) {
        sleep$dirtyBuild = b;
    }

    @Inject(method = "setDirty", at = @At("HEAD"))
    private void sleep$dirty(boolean playerChanged, CallbackInfo ci) {
        sleep$epoch++;
        dev.sleep.optsupport.SectionLists.dirtied((SectionRenderDispatcher.RenderSection) (Object) this);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sleep$tag(CallbackInfo ci) {
        for (var e : buffers.entrySet()) ((ChunkBufferTag) e.getValue()).sleep$tag((SectionRenderDispatcher.RenderSection) (Object) this, e.getKey());
    }

    @Inject(method = "setCompiled", at = @At("HEAD"))
    private void sleep$compiled(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
        ChunkMultiDraw.compiled((SectionRenderDispatcher.RenderSection) (Object) this, compiled);
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void sleep$reset(CallbackInfo ci) {
        sleep$epoch++;
        dev.sleep.optsupport.SectionLists.changed();
        dev.sleep.optsupport.SectionLists.dirtied((SectionRenderDispatcher.RenderSection) (Object) this);   // reset sets dirty
        ChunkMultiDraw.release((SectionRenderDispatcher.RenderSection) (Object) this);
    }
}
