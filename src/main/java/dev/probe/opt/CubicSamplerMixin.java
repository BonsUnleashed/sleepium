package dev.probe.opt;

import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * exp/cubic-sampler (client): the fog / sky colour blur (216 fetches per call, every frame) without the two Vec3
 * objects vanilla makes per sample. {@code vec3 = vec3.add(fetched.scale(w))} is {@code s += f * w} per component, in
 * the same order on the same doubles (Vec3.ZERO + a is a), and the final {@code scale(1 / total)} the same product,
 * so the result is identical. The fetcher (mods may change the colours through the dimension effects) is called as
 * before.
 */
@Mixin(CubicSampler.class)
public abstract class CubicSamplerMixin {
    @Shadow @Final private static double[] GAUSSIAN_SAMPLE_KERNEL;

    /**
     * @author probe
     * @reason the same sums without two allocations per sample (exact, see the class comment)
     */
    @Overwrite
    public static Vec3 gaussianSampleVec3(Vec3 vec, CubicSampler.Vec3Fetcher fetcher) {
        int i = Mth.floor(vec.x());
        int j = Mth.floor(vec.y());
        int k = Mth.floor(vec.z());
        double d0 = vec.x() - (double) i;
        double d1 = vec.y() - (double) j;
        double d2 = vec.z() - (double) k;
        double d3 = 0.0;
        double sx = 0.0, sy = 0.0, sz = 0.0;
        double[] kernel = GAUSSIAN_SAMPLE_KERNEL;
        for (int l = 0; l < 6; l++) {
            double d4 = Mth.lerp(d0, kernel[l + 1], kernel[l]);
            int i1 = i - 2 + l;
            for (int j1 = 0; j1 < 6; j1++) {
                double d5 = Mth.lerp(d1, kernel[j1 + 1], kernel[j1]);
                int k1 = j - 2 + j1;
                for (int l1 = 0; l1 < 6; l1++) {
                    double d6 = Mth.lerp(d2, kernel[l1 + 1], kernel[l1]);
                    int i2 = k - 2 + l1;
                    double d7 = d4 * d5 * d6;
                    d3 += d7;
                    Vec3 f = fetcher.fetch(i1, k1, i2);
                    sx = sx + f.x * d7;
                    sy = sy + f.y * d7;
                    sz = sz + f.z * d7;
                }
            }
        }
        double inv = 1.0 / d3;
        return new Vec3(sx * inv, sy * inv, sz * inv);
    }
}
