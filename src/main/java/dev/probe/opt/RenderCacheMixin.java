package dev.probe.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.probe.optsupport.RenderCache;
import dev.probe.optsupport.sleep.EntityFp;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/render-cache (client): the entity renderer call of EntityRenderDispatcher.render goes through RenderCache (the
 * dispatcher's own flame, shadow and hitbox stay as they are). Key: synched data and equipment; the yaw argument.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class RenderCacheMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void probe$cached(EntityRenderer r, Entity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        long key = RenderCache.keyThisTick(e);
        if (key == RenderCache.NO_KEY) {
            key = EntityFp.fp(e.getEntityData());
            if (e instanceof LivingEntity le) {
                for (ItemStack s : le.getArmorSlots()) key = key * 31 + EntityFp.fp(s);
                for (ItemStack s : le.getHandSlots()) key = key * 31 + EntityFp.fp(s);
            }
            RenderCache.keyComputed(e, key);
        }
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            RenderCache.render(e, r.getClass(), (p, b, t) -> r.render(e, yaw, t, p, b, light), pt, pose, buffers, light, OverlayTexture.NO_OVERLAY, yaw, key,
                    (EntityRenderDispatcher) (Object) this);
            return;
        }
        var d = dev.probe.optsupport.RenderCacheDraws.enterEntity(r, e, yaw, light);   // no lambda per call
        try {
            RenderCache.render(e, r.getClass(), d, pt, pose, buffers, light, OverlayTexture.NO_OVERLAY, yaw, key, (EntityRenderDispatcher) (Object) this);
        } finally {
            dev.probe.optsupport.RenderCacheDraws.exitEntity(d);
        }
    }
}

