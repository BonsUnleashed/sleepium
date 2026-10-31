package dev.sleep.geomixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sleep.optsupport.GeoParts;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

/**
 * GeckoLib's base renderers get their own renderCubesOfBone (GeoRenderer's default, with a first step): the bone from
 * VRAM when GeoParts can take it, else GeoRenderer's default body. A mod's renderer that overrides renderCubesOfBone
 * keeps its own.
 */
@Mixin(value = {GeoEntityRenderer.class, GeoBlockRenderer.class, GeoReplacedEntityRenderer.class, GeoObjectRenderer.class,
        GeoItemRenderer.class, GeoArmorRenderer.class}, remap = false)
public abstract class GeoCubesMixin {
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.isHidden()) return;
        GeoRenderer<?> self = (GeoRenderer<?>) (Object) this;
        if (GeoParts.capture(self, poseStack, bone, buffer, packedLight, packedOverlay, colour)) return;
        for (GeoCube cube : bone.getCubes()) {   // GeoRenderer's default body
            poseStack.pushPose();
            self.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
            poseStack.popPose();
        }
    }
}
