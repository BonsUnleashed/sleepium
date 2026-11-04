package dev.lull.opt;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.lull.optsupport.PartBatch;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PartBatchMixin: which render type's batch is being drawn (1.20.1: RenderType.end draws a builder). */
@Mixin(RenderType.class)
public abstract class PartTypeMixin {
    @Inject(method = "end", at = @At("HEAD"))
    private void sleep$begin(BufferBuilder builder, VertexSorting sorting, CallbackInfo ci) {
        PartBatch.beginType((RenderType) (Object) this);
    }

    @Inject(method = "end", at = @At("TAIL"))
    private void sleep$end(BufferBuilder builder, VertexSorting sorting, CallbackInfo ci) {
        PartBatch.endType();
    }
}
