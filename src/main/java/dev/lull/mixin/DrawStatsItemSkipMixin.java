package dev.lull.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test only (-Dsleep.drawStats -Dsleep.test.skipItemGeometry=true): items on the ground emit no geometry, the rest of
 * their entity's render (dispatch, light, pose) stays - the ceiling of drawing their models from VRAM.
 */
@Mixin(ItemRenderer.class)
public abstract class DrawStatsItemSkipMixin {
    private static final boolean SKIP = Boolean.getBoolean("sleep.test.skipItemGeometry");

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void sleep$skip(ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (SKIP && ctx == ItemDisplayContext.GROUND) ci.cancel();
    }
}
