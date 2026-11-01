package dev.sleep.catmixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.PartBatch;
import net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.TemplateMesh;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's SuperByteBuffer from VRAM: renderInto transforms every vertex of a cached template on the CPU each call; here
 * the template goes to VRAM once (PartBatch's geometry, keyed by the template) and the call becomes a part record
 * (PartBatch.captureSbb: matrices, colour, light, overlay, diffuse on the GPU) when it can be: no UV shift (scrolling
 * belts), no light from the level (contraptions), a plain template, a batched BLOCK-format quad buffer - else the CPU
 * path as before. reset() as renderInto ends.
 */
@Mixin(value = ShadeSeparatingSuperByteBuffer.class, remap = false)
public abstract class SbbPartsMixin implements PartBatch.GeometrySource {
    @Shadow @Final private TemplateMesh template;
    @Shadow @Final private int[] shadeSwapVertices;
    @Shadow @Final private PoseStack transforms;
    @Shadow private float r;
    @Shadow private float g;
    @Shadow private float b;
    @Shadow private float a;
    @Shadow private boolean disableDiffuse;
    @Shadow private SuperByteBuffer.SpriteShiftFunc spriteShiftFunc;
    @Shadow private boolean hasCustomOverlay;
    @Shadow private int overlay;
    @Shadow private boolean hasCustomLight;
    @Shadow private int packedLight;
    @Shadow private boolean useLevelLight;
    @Shadow @Final private boolean invertFakeDiffuseNormal;

    @Shadow
    public abstract SuperByteBuffer reset();

    @Inject(method = "renderInto", at = @At("HEAD"), cancellable = true)
    private void sleep$parts(PoseStack input, VertexConsumer builder, CallbackInfo ci) {
        PartBatch.sbbCalls++;
        if (template.isEmpty()) return;
        if (spriteShiftFunc != null) {
            PartBatch.sbbShift++;
            return;
        }
        if (useLevelLight) { PartBatch.sbbLevelLight++; return; }
        if (dev.sleep.optsupport.RenderCache.recording()) {   // the render cache records it: a part draws it cheaper - directly
            dev.sleep.optsupport.RenderCache.partsWanted();
            return;
        }
        if (PartBatch.lightDirections == null) PartBatch.lightDirections = RenderSystemLightAccessor::sleep$lightDirections;
        boolean diffuse = !disableDiffuse && !dev.engine_room.flywheel.lib.util.ShadersModHelper.isShaderPackInUse();
        int light = hasCustomLight ? packedLight : 0;
        int ov = hasCustomOverlay ? overlay : net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
        if (PartBatch.captureSbb(template, this, null, input.last(), transforms.last(), r, g, b, a, diffuse, invertFakeDiffuseNormal ? -1.0F : 1.0F,
                light, ov, builder)) {
            reset();
            ci.cancel();
        }
    }

    /** The template in local space for PartBatch's geometry (with its per-vertex colour): false when a vertex has its own light. */
    @Override
    public boolean sleep$emit(Object unusedA, Object unusedB, VertexConsumer consumer) {
        int n = template.vertexCount();
        boolean shaded = true;
        int swap = 0, next = shadeSwapVertices.length > 0 ? shadeSwapVertices[0] : -1;
        for (int i = 0; i < n; i++) {
            if (template.light(i) != 0) return false;   // its own light (an emissive quad): the CPU path
            if (i == next) {
                shaded = !shaded;
                swap++;
                next = swap < shadeSwapVertices.length ? shadeSwapVertices[swap] : -1;
            }
            if (consumer instanceof PartBatch.Flagged f) f.sleep$flags(shaded ? 0 : 8);
            int pn = template.normal(i);
            consumer.addVertex(template.x(i), template.y(i), template.z(i));
            if (consumer instanceof PartBatch.Flagged f) f.sleep$rawColor(template.color(i));   // bytes R, G, B, A as renderInto reads them
            consumer.setUv(template.u(i), template.v(i))
                    .setNormal((byte) (pn & 0xFF) / 127.0F, (byte) (pn >>> 8 & 0xFF) / 127.0F, (byte) (pn >>> 16 & 0xFF) / 127.0F);
        }
        return true;
    }
}
