package dev.lull.opt;

import dev.lull.optsupport.LightScout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Measurement only (lab.lightscout): section compile boundaries. 1.20.1: the compile is RenderChunk.RebuildTask.compile
 * (1.21.1: SectionCompiler.compile).
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class LightScoutMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void sleep$begin(CallbackInfoReturnable<?> cir) {
        LightScout.begin();
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private void sleep$end(CallbackInfoReturnable<?> cir) {
        LightScout.end();
        if (LightScout.sections.get() % 2000 == 0) System.out.println("[sleep/lightscout] " + LightScout.report());
    }
}
