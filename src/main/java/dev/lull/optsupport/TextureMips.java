package dev.lull.optsupport;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL30C;

/**
 * Mipmaps for model textures a game asks for (entities and worn pieces have none in vanilla: a fine pattern - mail's
 * rings, a blade's edge line - seen from afar shimmers as noise, a texel a screen pixel one frame and none the next).
 * A texture whose location starts with a registered prefix gets its mip levels built when it loads and is drawn
 * nearest within a level, linear between levels (vanilla's block atlas filter): crisp texels up close, a calm average
 * far off. Nothing registered: nothing changes.
 */
public final class TextureMips {
    private TextureMips() {}

    private static final List<String> PREFIXES = new CopyOnWriteArrayList<>();

    /** textures of this namespace under this path (e.g. "textures/equipment/") get mipmaps */
    public static void register(String namespace, String pathPrefix) {
        PREFIXES.add(namespace + ":" + pathPrefix);
    }

    public static boolean wants(ResourceLocation rl) {
        if (rl == null || PREFIXES.isEmpty()) return false;
        String s = rl.toString();
        for (String p : PREFIXES) if (s.startsWith(p)) return true;
        return false;
    }

    /** a texture's own mark (AbstractTexture, by a mixin): drawn with its mip levels */
    public interface Flag {
        boolean sleep$mips();

        void sleep$setMips(boolean on);
    }

    /**
     * Before the upload: the colour of the drawn texels spread into the clear ones round them (their alpha kept 0) - a
     * level's average then takes no black from the clear (the faces' edges would darken far off)
     */
    public static void bleed(com.mojang.blaze3d.platform.NativeImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = im.getPixelRGBA(x, y);
        boolean[] filled = new boolean[w * h];
        for (int i = 0; i < px.length; i++) filled[i] = (px[i] >>> 24) != 0;
        for (int pass = 0; pass < 8; pass++) {
            int[] next = px.clone();
            boolean[] nf = filled.clone();
            boolean any = false;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (filled[i]) continue;
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= w || yy >= h || !filled[yy * w + xx]) continue;
                            int c = px[yy * w + xx];   // ABGR
                            r += c & 0xFF;
                            g += c >> 8 & 0xFF;
                            b += c >> 16 & 0xFF;
                            n++;
                        }
                    if (n == 0) continue;
                    next[i] = (px[i] & 0xFF000000) | (b / n) << 16 | (g / n) << 8 | (r / n);
                    nf[i] = true;
                    any = true;
                }
            px = next;
            filled = nf;
            if (!any) break;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) im.setPixelRGBA(x, y, px[y * w + x]);
    }

    /** the levels of the bound-by-id texture, from its level 0 as uploaded */
    public static void build(int id) {
        GlStateManager._bindTexture(id);
        int w = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
        int h = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
        int levels = 31 - Integer.numberOfLeadingZeros(Math.max(1, Math.min(w, h)));
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, levels);
        GL30C.glGenerateMipmap(GL11C.GL_TEXTURE_2D);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST_MIPMAP_LINEAR);
    }
}
