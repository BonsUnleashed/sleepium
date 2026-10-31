package dev.sleep.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.EntityBatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
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
 */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class EntityBatchMixin {
    @Shadow @Final protected SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
    @Shadow @Final protected Map<RenderType, BufferBuilder> startedBuilders;
    @Shadow protected RenderType lastSharedType;

    @Shadow public abstract void endBatch(RenderType renderType);
    @Shadow
    private void endBatch(RenderType renderType, BufferBuilder builder) {
        throw new AssertionError();
    }

    @Unique private final Map<RenderType, ByteBufferBuilder> sleep$buffers = new HashMap<>();
    @Unique private final List<RenderType> sleep$deferred = new ArrayList<>();
    @Unique private boolean sleep$flushing;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void sleep$getBuffer(RenderType type, CallbackInfoReturnable<VertexConsumer> cir) {
        BufferBuilder b = startedBuilders.get(type);
        if (b != null) {
            if (sleep$deferred.contains(type)) cir.setReturnValue(b);
            return;
        }
        if (fixedBuffers.containsKey(type) || !EntityBatch.commutes(type)) return;
        ByteBufferBuilder bb = sleep$buffers.get(type);
        if (bb == null) {
            if (sleep$buffers.size() >= EntityBatch.MAX_TYPES) return;   // vanilla's shared buffer for the rest
            sleep$buffers.put(type, bb = new ByteBufferBuilder(type.bufferSize()));
        }
        // vanilla ends the shared batch on this switch: so do we (a blending batch keeps its place)
        if (lastSharedType != null) endBatch(lastSharedType);
        b = new BufferBuilder(bb, type.mode(), type.format());
        startedBuilders.put(type, b);
        EntityBatch.started(b, type);
        sleep$deferred.add(type);
        EntityBatch.batches++;
        cir.setReturnValue(b);
    }

    /** Every draw of this source: the deferred batches first (unless it is one of them). */
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/BufferBuilder;)V", at = @At("HEAD"))
    private void sleep$beforeDraw(RenderType type, BufferBuilder builder, CallbackInfo ci) {
        if (sleep$flushing) return;
        if (sleep$deferred.remove(type)) {
            EntityBatch.ended(builder);
            EntityBatch.draws++;
            return;
        }
        sleep$flush();
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
            for (RenderType t : sleep$deferred) {
                BufferBuilder b = startedBuilders.remove(t);
                if (b != null) {
                    EntityBatch.ended(b);
                    endBatch(t, b);
                    EntityBatch.draws++;
                }
            }
            sleep$deferred.clear();
        } finally {
            sleep$flushing = false;
        }
    }
}
