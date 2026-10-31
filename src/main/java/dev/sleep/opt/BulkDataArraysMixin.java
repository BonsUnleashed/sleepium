package dev.sleep.opt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/bulk-arrays (client): the short putBulkData overloads build new float[]{1,1,1,1} and int[]{light x4} per quad
 * (13 % of the render thread's allocation in an entity-heavy scene: items in frames, held items, armour). At these two
 * call sites the full overload is called with arrays reused per thread and refilled every call. Exact as long as the
 * consumer does not keep the arrays (vanilla's reads them at once); opt-in for that reason.
 */
@Mixin({ItemRenderer.class, ModelBlockRenderer.class})
public abstract class BulkDataArraysMixin {
    @Unique private static final ThreadLocal<float[]> PROBE$BRIGHT = ThreadLocal.withInitial(() -> new float[4]);
    @Unique private static final ThreadLocal<int[]> PROBE$LIGHT = ThreadLocal.withInitial(() -> new int[4]);

    @Redirect(method = "renderQuadList", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIZ)V"))
    private void probe$items(VertexConsumer c, PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, float a, int light, int overlay, boolean existing) {
        if (dev.sleep.optsupport.BulkData.SHORT9_DEFAULT.get(c.getClass())) c.putBulkData(pose, quad, probe$bright(), r, g, b, a, probe$light(light), overlay, existing);
        else c.putBulkData(pose, quad, r, g, b, a, light, overlay, existing);   // the consumer overrides it: its own code
    }

    @Redirect(method = "renderQuadList", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V"))
    private static void probe$blocks(VertexConsumer c, PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, float a, int light, int overlay) {
        if (dev.sleep.optsupport.BulkData.SHORT8_DEFAULT.get(c.getClass())) c.putBulkData(pose, quad, probe$bright(), r, g, b, a, probe$light(light), overlay, false);
        else c.putBulkData(pose, quad, r, g, b, a, light, overlay);
    }

    @Unique
    private static float[] probe$bright() {
        float[] f = PROBE$BRIGHT.get();
        f[0] = f[1] = f[2] = f[3] = 1.0F;
        return f;
    }

    @Unique
    private static int[] probe$light(int light) {
        int[] l = PROBE$LIGHT.get();
        l[0] = l[1] = l[2] = l[3] = light;
        return l;
    }
}
