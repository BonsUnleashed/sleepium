package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/frame-garbage (client): Forge's EntityRenderer.render builds the entity's display name (components, styles, hover
 * event, the UUID as a string) and a RenderNameTagEvent for every entity in every frame, and posts it, only to ask
 * shouldShowName when nobody listens (the event's result stays DEFAULT). With no listener of the event and
 * shouldShowName false the outcome is known - no tag, nothing else observable - and nothing is built; otherwise
 * Forge's steps exactly. Listeners are asked every call (a mod may register one later).
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagSkipMixin<T extends Entity> {
    @Shadow
    protected abstract boolean shouldShowName(T entity);

    @Shadow
    protected abstract void renderNameTag(T entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight);

    /**
     * @author probe (lab.opt.NameTagSkipMixin)
     * @reason no display name and event built for a tag that cannot show
     */
    @Overwrite
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!dev.lull.optsupport.EventSites.nameTagListened() && !this.shouldShowName(entity)) return;
        var renderNameTagEvent = new net.minecraftforge.client.event.RenderNameTagEvent(entity, entity.getDisplayName(), (EntityRenderer<?>) (Object) this, poseStack, buffer, packedLight, partialTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(renderNameTagEvent);
        if (renderNameTagEvent.getResult() != net.minecraftforge.eventbus.api.Event.Result.DENY && (renderNameTagEvent.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW || this.shouldShowName(entity))) {
            this.renderNameTag(entity, renderNameTagEvent.getContent(), poseStack, buffer, packedLight);
        }
    }
}
