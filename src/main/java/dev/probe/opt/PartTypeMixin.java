package dev.probe.opt;

import com.mojang.blaze3d.vertex.MeshData;
import dev.probe.optsupport.PartBatch;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PartBatchMixin: which render type's batch is being drawn. */
@Mixin(RenderType.class)
public abstract class PartTypeMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void probe$begin(MeshData mesh, CallbackInfo ci) {
        PartBatch.beginType((RenderType) (Object) this);
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void probe$end(MeshData mesh, CallbackInfo ci) {
        PartBatch.endType();
    }
}
