package dev.sleep.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import dev.sleep.DrawStats;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dprobe.drawStats: every immediate-mode batch drawn by a render type. */
@Mixin(RenderType.class)
public abstract class DrawStatsMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void probe$draw(MeshData mesh, CallbackInfo ci) {
        DrawStats.draw(this.toString(), mesh.drawState().vertexCount(), mesh.vertexBuffer().remaining());
    }
}
