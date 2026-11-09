package testmods.poseleak;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test mode (-Dsleep.test.poseLeak=Pig): a broken renderer of another mod - the named entity class' rendering pushes a
 * pose and never pops it, as the one behind GitHub #5 did; the game stops with "Pose stack not empty" and the log must
 * name the entity and its renderer first.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PoseLeakMixin {
    @org.spongepowered.asm.mixin.Unique private static final String SLEEP_LEAK = System.getProperty("sleep.test.poseLeak");

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void sleep$leak(LivingEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (e.getClass().getSimpleName().equals(SLEEP_LEAK)) pose.pushPose();
    }
}
