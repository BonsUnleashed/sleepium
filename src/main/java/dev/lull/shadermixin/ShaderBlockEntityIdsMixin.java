package dev.lull.shadermixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lull.shaders.ShaderIds;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The block entity id of a shaderpack (its block's block.properties id) on the vertices of each block entity render. */
@Mixin(BlockEntityRenderDispatcher.class)
public class ShaderBlockEntityIdsMixin {
    @Unique
    private static int sleep$saved;

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void sleep$blockEntityId(E be, float pt, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
        if (!ShaderIds.on) return;
        sleep$saved = ShaderIds.slot1;
        int id = ShaderIds.maps.block(be.getBlockState());
        ShaderIds.slot1 = ShaderIds.maps.hasBlocks ? (id >= 0 ? id : 65535) : 0;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$blockEntityIdEnd(CallbackInfo ci) {
        if (ShaderIds.on) ShaderIds.slot1 = sleep$saved;
    }
}
