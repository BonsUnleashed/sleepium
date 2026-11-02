package dev.lull.catmixin;

import dev.lull.optsupport.ParallelRender;
import java.util.concurrent.Callable;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Parallel render: catnip's cached SuperByteBuffers are one mutable object per model, shared by every renderer (their
 * transforms, colour and shift are set per call): a renderer that takes one inside a worker job races with the render
 * thread (Create's contraptions with Flywheel's backend off: corrupted pose stacks there). Refused before it is touched:
 * the entity type stays on the render thread.
 */
@Mixin(value = SuperByteBufferCache.class, remap = false)
public abstract class SbbCacheWorkerMixin {
    @Inject(method = "get", at = @At("HEAD"))
    private <T> void sleep$renderThreadOnly(SuperByteBufferCache.Compartment<T> compartment, T key, Callable<SuperByteBuffer> supplier,
            CallbackInfoReturnable<SuperByteBuffer> cir) {
        if (ParallelRender.inJob()) throw ParallelRender.REFUSE_SHARED;
    }
}
