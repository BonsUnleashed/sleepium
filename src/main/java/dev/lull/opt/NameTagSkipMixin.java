package dev.lull.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/frame-garbage (client): NeoForge's EntityRenderer.render builds the entity's display name (components, styles,
 * hover event, the UUID as a string) and a RenderNameTagEvent for every entity in every frame, and posts it, only to
 * ask shouldShowName when nobody listens. With no listener of the event and shouldShowName false the outcome is
 * known - no tag, nothing else observable (the post reaches no one; display name and shouldShowName have no side
 * effects) - and nothing is built; otherwise vanilla's steps exactly. Listeners are asked every call (a mod may
 * register one later).
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagSkipMixin<T extends Entity> {
    @Shadow
    protected abstract boolean shouldShowName(T entity);

    @Shadow
    protected abstract void renderNameTag(T entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick);

    @Shadow
    private <E extends Entity> void renderLeash(T entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, E leashHolder) {
        throw new AssertionError();
    }

    /**
     * @author probe (lab.opt.NameTagSkipMixin)
     * @reason no display name and event built for a tag that cannot show
     */
    @Overwrite
    public void render(T p_entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (p_entity instanceof Leashable leashable) {
            Entity entity = leashable.getLeashHolder();
            if (entity != null) {
                this.renderLeash(p_entity, partialTick, poseStack, bufferSource, entity);
            }
        }

        if (!dev.lull.optsupport.EventSites.nameTagListened() && !this.shouldShowName(p_entity)) return;
        // Neo: Post the RenderNameTagEvent and conditionally wrap #renderNameTag based on the result.
        var event = new net.neoforged.neoforge.client.event.RenderNameTagEvent(p_entity, p_entity.getDisplayName(), (EntityRenderer<?>) (Object) this, poseStack, bufferSource, packedLight, partialTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.canRender().isTrue() || event.canRender().isDefault() && this.shouldShowName(p_entity)) {
            this.renderNameTag(p_entity, event.getContent(), poseStack, bufferSource, packedLight, partialTick);
        }
    }
}
