package dev.sleep.shadermixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleep.shaders.ShaderIds;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** currentRenderedItemId of a shaderpack (item.properties) on the vertices of each item render. */
@Mixin(ItemRenderer.class)
public class ShaderItemIdsMixin {
    @Unique
    private static int sleep$saved;

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"))
    private void sleep$itemId(ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model,
            CallbackInfo ci) {
        if (!ShaderIds.on) return;
        sleep$saved = ShaderIds.item1;
        ShaderIds.item1 = ShaderIds.maps.item(stack.getItem()) + 1;
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("RETURN"))
    private void sleep$itemIdEnd(CallbackInfo ci) {
        if (ShaderIds.on) ShaderIds.item1 = sleep$saved;
    }
}
