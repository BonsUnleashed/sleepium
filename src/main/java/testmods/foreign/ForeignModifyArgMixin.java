package testmods.foreign;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Stand-in for another mod's mixin (a player's crash on 1.20.1, CurseForge 11-07: mixins.marrelics.json): a @ModifyArg on
 * the entity renderer call in EntityRenderDispatcher.render, applied after Sleepium's mixins. Only applied with
 * -Dsleep.foreignTest=true. Sleepium's @Redirect of the same call (RenderCacheMixin) must give way - up to 1.1.4 it did
 * not, the call became the redirect handler's (the renderer first) and this one failed at load ("Specified index 4 for
 * @ModifyArg is invalid").
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 2000)
public abstract class ForeignModifyArgMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            index = 4)
    private MultiBufferSource foreign$buffers(MultiBufferSource buffers) {
        return buffers;
    }
}
