package dev.sleep.shadermixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleep.shaders.ShaderIds;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An armour piece's item id (currentRenderedItemId) on its vertices, as Iris gives it (packs pick armour materials by it). */
@Mixin(HumanoidArmorLayer.class)
public class ShaderArmorIdsMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Unique
    private static int sleep$saved;

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"))
    private void sleep$armorId(PoseStack pose, MultiBufferSource buffers, T entity, EquipmentSlot slot, int light, A model, float a, float b, float c, float d,
            float e, float f, CallbackInfo ci) {
        if (!ShaderIds.on) return;
        sleep$saved = ShaderIds.item1;
        ShaderIds.item1 = ShaderIds.maps.item(entity.getItemBySlot(slot).getItem()) + 1;
    }

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("RETURN"))
    private void sleep$armorIdEnd(CallbackInfo ci) {
        if (ShaderIds.on) ShaderIds.item1 = sleep$saved;
    }
}
