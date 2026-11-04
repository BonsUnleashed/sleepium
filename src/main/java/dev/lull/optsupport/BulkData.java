package dev.lull.optsupport;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Which VertexConsumer classes keep the default short putBulkData overloads (BulkDataArraysMixin redirects only those),
 * and the arrays it passes instead (1.20.1: here, shared by its two mixins; 1.21.1: in the one mixin).
 * 1.20.1: the blocks' short overload has no alpha (7 arguments); the items' 9-argument one is Forge's (IForgeVertexConsumer).
 */
public final class BulkData {
    /** the 7-argument overload (VertexConsumer default; 1.21.1: 8 with alpha) */
    public static final ClassValue<Boolean> SHORT7_DEFAULT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            return declaredBy(c, new Class<?>[] {PoseStack.Pose.class, BakedQuad.class, float.class, float.class, float.class, int.class, int.class},
                    VertexConsumer.class);
        }
    };
    /** the 9-argument Forge overload (IForgeVertexConsumer default) */
    public static final ClassValue<Boolean> SHORT9_DEFAULT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            return declaredBy(c, new Class<?>[] {PoseStack.Pose.class, BakedQuad.class, float.class, float.class, float.class, float.class, int.class, int.class,
                    boolean.class}, net.minecraftforge.client.extensions.IForgeVertexConsumer.class);
        }
    };

    private BulkData() {}

    private static final ThreadLocal<float[]> BRIGHT = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<int[]> LIGHT = ThreadLocal.withInitial(() -> new int[4]);

    /** the brightness array the short overloads would build ({1, 1, 1, 1}), reused per thread and refilled */
    public static float[] bright() {
        float[] f = BRIGHT.get();
        f[0] = f[1] = f[2] = f[3] = 1.0F;
        return f;
    }

    /** the light array the short overloads would build (light x4), reused per thread */
    public static int[] light(int light) {
        int[] l = LIGHT.get();
        l[0] = l[1] = l[2] = l[3] = light;
        return l;
    }

    private static boolean declaredBy(Class<?> c, Class<?>[] params, Class<?> expected) {
        try {
            return c.getMethod("putBulkData", params).getDeclaringClass() == expected;
        } catch (NoSuchMethodException | RuntimeException e) {
            return false;
        }
    }
}
