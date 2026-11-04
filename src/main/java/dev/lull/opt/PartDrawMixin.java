package dev.lull.opt;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.lull.optsupport.PartBatch;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PartBatchMixin: the queued parts of the render type being drawn, right after its batch (program still bound). */
@Mixin(VertexBuffer.class)
public abstract class PartDrawMixin {
    @Inject(method = "_drawWithShader", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V", shift = At.Shift.AFTER))
    private void sleep$parts(Matrix4f modelView, Matrix4f projection, ShaderInstance shader, CallbackInfo ci) {
        PartBatch.afterDraw(shader);
    }
}
