package dev.lull.opt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lull.optsupport.ItemGpu;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** exp/item-gpu (client): an item model in the world as a record drawn from VRAM (ItemGpu) instead of its vertices. */
@Mixin(ItemRenderer.class)
public abstract class ItemGpuMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void sleep$model(ItemRenderer self, BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer vc, Operation<Void> original,
                             @Local(argsOnly = true) ItemDisplayContext ctx, @Local(argsOnly = true) MultiBufferSource buffers, @Local RenderType type) {
        if (!ItemGpu.capture(model, stack, light, overlay, pose, vc, type, ctx, buffers)) original.call(self, model, stack, light, overlay, pose, vc);
    }
}
