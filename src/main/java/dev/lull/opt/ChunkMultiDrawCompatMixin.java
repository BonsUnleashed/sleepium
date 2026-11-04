package dev.lull.opt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.optsupport.ChunkMultiDraw;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk multi-draw when another mod changes renderChunkLayer (ChunkMultiDraw.ACTIVE false: Sable / Aeronautics draw
 * ships in it, Veil hooks it): vanilla's method runs as it is, with every mod's injections; only its per-section loop
 * is replaced - right after the shader's apply() the layer is drawn by multi-draw, and the loop then finds nothing
 * left. When multi-draw cannot draw the layer (e.g. a shader recompiled without our patch), the loop draws it vanilla's
 * way: the vanilla buffers are kept uploaded in this mode. (1.20.1's loop tests hasNext() for the opaque layers, the
 * only ones this draws, and hasPrevious() for translucent.)
 */
@Mixin(LevelRenderer.class)
@SuppressWarnings("rawtypes")
public abstract class ChunkMultiDrawCompatMixin {
    @Shadow @Final private ObjectArrayList renderChunksInFrustum;
    @Unique private static boolean sleep$drawn;

    @Inject(method = "renderChunkLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V", shift = At.Shift.AFTER))
    private void sleep$multi(RenderType renderType, PoseStack poseStack, double x, double y, double z, Matrix4f projectionMatrix, CallbackInfo ci) {
        sleep$drawn = false;
        if (ChunkMultiDraw.ACTIVE || !ChunkMultiDraw.layerSupported(renderType) || ChunkMultiDraw.PATCHED.isEmpty()) return;
        sleep$drawn = ChunkMultiDraw.draw(renderType, this.renderChunksInFrustum, x, y, z, RenderSystem.getShader());
    }

    @Redirect(method = "renderChunkLayer", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectListIterator;hasNext()Z"))
    private boolean sleep$loop(ObjectListIterator it) {
        if (sleep$drawn) {
            sleep$drawn = false;
            return false;
        }
        return it.hasNext();
    }
}
