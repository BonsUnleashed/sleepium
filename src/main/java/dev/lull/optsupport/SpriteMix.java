package dev.lull.optsupport;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

/**
 * SpriteInterpolateMixin: an interpolated animated texture's frame (mcmeta "interpolate": true) blended the way
 * SpriteContents.InterpolationData does - per mip level, per pixel the red, green and blue of the two frames mixed with
 * (int) (d * a + (1 - d) * b), alpha from the first - but over the images' memory directly instead of getPixelRGBA /
 * setPixelRGBA per pixel (each with its format, bounds and allocation checks), and through a table of all 256 x 256
 * mixes when the sprite is big enough for the table to pay (the same expression, so the same ints). Vanilla does this
 * on the render thread every client tick for every such sprite: Cursed Fate's 512 x 512 black flash particle, 262 144
 * pixels a tick, was 12 % of the render thread in a fight.
 */
public final class SpriteMix {
    private SpriteMix() {}

    public static long fast, slow, verified, mismatches;
    public static final boolean VERIFY = Boolean.getBoolean("lab.sprite.verify");
    private static final int[] TABLE = new int[256 * 256];   // render thread only (texture ticks)

    /** False (nothing written) when an image is not one vanilla's checks would pass: the caller blends the vanilla way. */
    public static boolean interpolate(NativeImage[] src, NativeImage[] dst, int width, int height, int fx0, int fy0, int fx1, int fy1, double d) {
        long pixels = 0;
        for (int k = 0; k < dst.length; k++) {
            int w = width >> k, h = height >> k;
            if (w < 1 || h < 1) continue;
            NativeImage s = src[k], o = dst[k];
            if (s == null || o == null || s.format() != NativeImage.Format.RGBA || o.format() != NativeImage.Format.RGBA || s.pixels == 0 || o.pixels == 0)
                return false;
            int x0 = fx0 * width >> k, y0 = fy0 * height >> k, x1 = fx1 * width >> k, y1 = fy1 * height >> k;
            if (x0 < 0 || y0 < 0 || x1 < 0 || y1 < 0 || x0 + w > s.getWidth() || x1 + w > s.getWidth() || y0 + h > s.getHeight() || y1 + h > s.getHeight()
                    || w > o.getWidth() || h > o.getHeight()) return false;
            pixels += (long) w * h;
        }
        boolean table = pixels * 3 > 2L * TABLE.length && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
        if (table) {
            double e = 1.0 - d;
            for (int a = 0; a < 256; a++)
                for (int b = 0; b < 256; b++) TABLE[a << 8 | b] = (int) (d * (double) a + e * (double) b);
        }
        for (int k = 0; k < dst.length; k++) {
            int w = width >> k, h = height >> k;
            if (w < 1 || h < 1) continue;
            NativeImage s = src[k], o = dst[k];
            long sw = s.getWidth(), ow = o.getWidth();
            long p0 = s.pixels + ((fx0 * width >> k) + (long) (fy0 * height >> k) * sw) * 4L;
            long p1 = s.pixels + ((fx1 * width >> k) + (long) (fy1 * height >> k) * sw) * 4L;
            long q = o.pixels;
            for (int y = 0; y < h; y++) {
                long r0 = p0 + y * sw * 4L, r1 = p1 + y * sw * 4L, out = q + y * ow * 4L;
                if (table) {
                    for (int x = 0; x < w; x++) {
                        int a = MemoryUtil.memGetInt(r0 + x * 4L), b = MemoryUtil.memGetInt(r1 + x * 4L);
                        int r = TABLE[(a >> 16 & 255) << 8 | (b >> 16 & 255)], g = TABLE[(a >> 8 & 255) << 8 | (b >> 8 & 255)], bl = TABLE[(a & 255) << 8 | (b & 255)];
                        MemoryUtil.memPutInt(out + x * 4L, a & 0xFF000000 | r << 16 | g << 8 | bl);
                    }
                } else {
                    for (int x = 0; x < w; x++) {
                        int a = MemoryUtil.memGetInt(r0 + x * 4L), b = MemoryUtil.memGetInt(r1 + x * 4L);
                        int r = mix(d, a >> 16 & 255, b >> 16 & 255), g = mix(d, a >> 8 & 255, b >> 8 & 255), bl = mix(d, a & 255, b & 255);
                        MemoryUtil.memPutInt(out + x * 4L, a & 0xFF000000 | r << 16 | g << 8 | bl);
                    }
                }
            }
        }
        fast++;
        return true;
    }

    /** vanilla's InterpolationData.mix */
    public static int mix(double d, int a, int b) {
        return (int) (d * (double) a + (1.0 - d) * (double) b);
    }
}
