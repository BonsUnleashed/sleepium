package dev.lull.opt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.EntityBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/entity-batch (client): immediate-mode draws batched per render type, for every renderer (vanilla or mod) that
 * draws through a BufferSource.
 *
 * <p>Vanilla keeps one shared buffer for render types without a fixed buffer, so every switch of type ends the batch:
 * an armour stand's body and its two armour layers alternate and give 3–4 draws per stand. A render type whose draws
 * commute with each other's (EntityBatch.commutes: no blending, depth test {@code <=} with depth writes, no colour
 * logic, main target, no upload sort) gets its own buffer here and is drawn once, before any other draw of this
 * source. So everything drawn before a non-commuting draw in vanilla is still drawn before it (blending sees the same
 * framebuffer); only commuting draws change order among themselves, and with depth writes and {@code <=} the result
 * is the nearest fragment whatever the order (ties: exactly coplanar fragments of different types; frame hash checks).
 *
 * <p>1.20.1: the source has one shared BufferBuilder, the fixed ones, the last type (lastState) and the set of started
 * builders; a draw is RenderType.end inside endBatch(RenderType). A batch here is its own BufferBuilder, in the started
 * set while it is open (what the render cache counts there includes it); lastState never names a batched type, so
 * vanilla's switch logic leaves the batches alone, as 1.21's lastSharedType does.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class EntityBatchMixin {
    @Shadow @Final protected Map<RenderType, BufferBuilder> fixedBuffers;
    @Shadow protected Optional<RenderType> lastState;
    @Shadow @Final protected Set<BufferBuilder> startedBuffers;

    @Shadow public abstract void endBatch(RenderType renderType);

    @Unique private final Map<RenderType, BufferBuilder> sleep$buffers = new HashMap<>();
    @Unique private final List<RenderType> sleep$deferred = new ArrayList<>();
    @Unique private boolean sleep$flushing;
    @Unique private static final boolean KEEP_SHARED = !"false".equals(System.getProperty("lab.ebatch.keepShared"));

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void sleep$getBuffer(RenderType type, CallbackInfoReturnable<VertexConsumer> cir) {
        // 1.20.1 ends the shared batch when a FIXED buffer's type is asked for (1.21's BufferSource does not): every
        // sign's text was drawn alone, as the next sign's board (a fixed sheet) came. Fixed buffers are drawn at the end
        // anyway, so nothing is drawn between: the fixed buffer is handed out as 1.21 does, the shared batch and
        // lastState stay (another shared type still ends it, as vanilla)
        if (KEEP_SHARED && lastState.isPresent() && !fixedBuffers.containsKey(lastState.get()) && type.canConsolidateConsecutiveGeometry()) {
            BufferBuilder fixed = fixedBuffers.get(type);
            if (fixed != null) {
                if (startedBuffers.add(fixed)) fixed.begin(type.mode(), type.format());
                cir.setReturnValue(fixed);
                return;
            }
        }
        BufferBuilder b = sleep$buffers.get(type);
        if (b != null && sleep$deferred.contains(type)) {
            cir.setReturnValue(b);
            return;
        }
        // a fixed buffer, a type that does not commute, or the shared batch going on with this type: vanilla
        if (fixedBuffers.containsKey(type) || !EntityBatch.commutes(type) || lastState.isPresent() && lastState.get() == type) return;
        if (b == null) {
            if (sleep$buffers.size() >= EntityBatch.MAX_TYPES) return;   // vanilla's shared buffer for the rest
            sleep$buffers.put(type, b = new BufferBuilder(type.bufferSize()));
        }
        // vanilla ends the shared batch on this switch; we keep it open (-Dlab.ebatch.keepShared=false: vanilla's end):
        // a batched type draws nothing now (its batch is drawn later), so nothing is drawn between this shared batch
        // and its next geometry - the shared type's later vertices join the same draw in the same order (one draw for
        // every sign's text, 1.20.1: 69 draws -> 1 on client_entities); another shared type still ends it as vanilla
        if (!KEEP_SHARED && lastState.isPresent() && !fixedBuffers.containsKey(lastState.get())) endBatch(lastState.get());
        b.begin(type.mode(), type.format());
        startedBuffers.add(b);
        EntityBatch.started(b, type);
        sleep$deferred.add(type);
        EntityBatch.batches++;
        cir.setReturnValue(b);
    }

    /** A batched type ended on its own (a renderer's endBatch(type)): its batch alone is drawn, as 1.21's would be. */
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"), cancellable = true)
    private void sleep$endOne(RenderType type, CallbackInfo ci) {
        if (sleep$flushing || !sleep$deferred.remove(type)) return;
        BufferBuilder b = sleep$buffers.get(type);
        startedBuffers.remove(b);
        EntityBatch.ended(b);
        type.end(b, RenderSystem.getVertexSorting());
        EntityBatch.draws++;
        ci.cancel();
    }

    /** Every draw of this source: the deferred batches first. */
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;end(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)V"))
    private void sleep$beforeDraw(RenderType type, CallbackInfo ci) {
        if (!sleep$flushing) sleep$flush();
    }

    @Inject(method = {"endLastBatch", "endBatch()V"}, at = @At("HEAD"))
    private void sleep$beforeEnd(CallbackInfo ci) {
        if (!sleep$flushing) sleep$flush();
    }

    @Unique
    private void sleep$flush() {
        if (sleep$deferred.isEmpty()) return;
        sleep$flushing = true;
        try {
            for (int i = 0; i < sleep$deferred.size(); i++) {
                RenderType t = sleep$deferred.get(i);
                BufferBuilder b = sleep$buffers.get(t);
                if (startedBuffers.remove(b)) {
                    EntityBatch.ended(b);
                    t.end(b, RenderSystem.getVertexSorting());
                    EntityBatch.draws++;
                }
            }
            sleep$deferred.clear();
        } finally {
            sleep$flushing = false;
        }
    }
}
