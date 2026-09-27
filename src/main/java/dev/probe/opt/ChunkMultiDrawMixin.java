package dev.probe.opt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.probe.optsupport.ChunkMultiDraw;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * exp/chunk-multidraw (client): the opaque chunk layers through ChunkMultiDraw (one multi-draw per region). The body is
 * vanilla renderSectionLayer for non-translucent layers (state setup, uniforms, the stage event, teardown), with the
 * per-section loop replaced; a layer whose shader is not patched keeps the vanilla loop.
 */
@Mixin(LevelRenderer.class)
public abstract class ChunkMultiDrawMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private int ticks;
    @Shadow public abstract net.minecraft.client.renderer.culling.Frustum getFrustum();

    @Unique private static final boolean ACTIVE = !dev.probe.optsupport.MixinTouch.touched("net/minecraft/client/renderer/LevelRenderer", "renderSectionLayer");

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void probe$multi(RenderType renderType, double x, double y, double z, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!ACTIVE || !ChunkMultiDraw.layerSupported(renderType) || ChunkMultiDraw.PATCHED.isEmpty()) return;
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();
        var profiler = this.minecraft.getProfiler();
        profiler.push("filterempty");
        profiler.popPush(() -> "render_" + renderType);
        ShaderInstance shader = RenderSystem.getShader();
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, frustumMatrix, projectionMatrix, this.minecraft.getWindow());
        shader.apply();
        var uniform = shader.CHUNK_OFFSET;
        if (!ChunkMultiDraw.draw(renderType, this.visibleSections, x, y, z, shader)) {
            for (SectionRenderDispatcher.RenderSection s : this.visibleSections) {   // vanilla loop
                if (s.getCompiled().isEmpty(renderType)) continue;
                VertexBuffer vb = s.getBuffer(renderType);
                BlockPos o = s.getOrigin();
                if (uniform != null) {
                    uniform.set((float) ((double) o.getX() - x), (float) ((double) o.getY() - y), (float) ((double) o.getZ() - z));
                    uniform.upload();
                }
                vb.bind();
                vb.draw();
            }
        }
        if (uniform != null) uniform.set(0.0F, 0.0F, 0.0F);
        shader.clear();
        VertexBuffer.unbind();
        profiler.pop();
        net.neoforged.neoforge.client.ClientHooks.dispatchRenderStage(renderType, (LevelRenderer) (Object) this, frustumMatrix, projectionMatrix, this.ticks,
                this.minecraft.gameRenderer.getMainCamera(), this.getFrustum());
        renderType.clearRenderState();
        ci.cancel();
    }
}
