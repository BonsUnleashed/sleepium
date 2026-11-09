package dev.lull.opt;

import dev.lull.optsupport.NoAllocChecks;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** exp/frame-garbage (client): renderLevel's block-entity frustum tests through NoAllocChecks (no AABB for the default box). */
@Mixin(LevelRenderer.class)
public abstract class BlockEntityCullNoAllocMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;isBlockEntityRendererVisible(Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/client/renderer/culling/Frustum;)Z"))
    private boolean sleep$visible(BlockEntityRenderDispatcher dispatcher, BlockEntity be, Frustum frustum) {
        return NoAllocChecks.blockEntityVisible(dispatcher, be, frustum);
    }
}
