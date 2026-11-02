package dev.sleep.shadermixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleep.shaders.ShaderIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The entity id of a shaderpack (entity.properties) on the vertices of each entity render. */
@Mixin(EntityRenderDispatcher.class)
public class ShaderEntityIdsMixin {
    @Unique
    private static final int[] sleep$saved = new int[64];
    @Unique
    private static int sleep$depth;

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void sleep$entityId(E entity, double x, double y, double z, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light,
            CallbackInfo ci) {
        if (!ShaderIds.on) return;
        if (sleep$depth < sleep$saved.length) sleep$saved[sleep$depth] = ShaderIds.slot1;
        sleep$depth++;
        ShaderIds.slot1 = ShaderIds.maps.entity(entity.getType(), entity == Minecraft.getInstance().getCameraEntity());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$entityIdEnd(CallbackInfo ci) {
        if (sleep$depth == 0) return;
        sleep$depth--;
        if (sleep$depth < sleep$saved.length) ShaderIds.slot1 = sleep$saved[sleep$depth];
    }
}
