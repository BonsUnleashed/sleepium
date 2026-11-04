package dev.lull.optsupport;

import java.util.function.ToLongFunction;
import net.minecraft.world.entity.Entity;

/**
 * Surface coatings on entities (client): what lies on a body and what it wears - wet, mud, snow, ice - drawn by the
 * material shading (Materials) over the textures, so armour takes them as the body does. A game registers
 * {@link #provider}: an entity's coatings packed in a long, 6 bits each (0..63): wet, mud, snow, ice, blood, soot, a
 * glow and its hue (at {@link #WET} .. {@link #GLOW_HUE}; the glow lights only the texels its material mask marks for
 * it, Materials); 0 is none. The amounts reach the shader through PartBatch's per-draw records
 * (the normal matrix columns' spare w: n0.w the first four, n1.w the next four - 24 bits each, exact in a float - n2.w
 * the entity's height), so only part-batched geometry (vanilla parts, GeckoLib bones)
 * shows them, as with materials. An entity with a coating is drawn live, not from the render cache (its recorded
 * vertices carry no records); one without draws exactly as before (the shader's branch is off).
 * {@code -Dsleep.coatings=false} turns them off.
 * <p>
 * 10-07 (one's own armour showing its damage): the third word (n2.w) carries the height in its low 8 bits (tenths of
 * a block) and the blows' marks above them: a game's {@link #damage} - three 4-bit amounts (the head, the body, the
 * legs: where on the body by the height up the model) the shader draws as dents, gouges and cracks on the metal its
 * material mask marks, cuts and grime on the rest.
 */
public final class Coatings {
    private Coatings() {}

    public static final boolean ON = !"false".equals(System.getProperty("sleep.coatings"));
    public static final int WET = 0, MUD = 6, SNOW = 12, ICE = 18, BLOOD = 24, SOOT = 30, GLOW = 36, GLOW_HUE = 42;

    /** the game's: an entity's packed coatings (render thread or a ParallelRender worker; must not touch the world) */
    public static volatile ToLongFunction<Entity> provider;
    /** the game's: an entity's blows' marks - head | body << 4 | legs << 8, each 0..15 (the same threads' rules) */
    public static volatile java.util.function.ToIntFunction<Entity> damage;

    /** the entity's marks (0: none) */
    public static int damage(Entity e) {
        var d = damage;
        return !ON || d == null || e == null ? 0 : d.applyAsInt(e) & 0xFFF;
    }

    /** the third word: the height (tenths of a block, low 8 bits) and the marks; 0 when there is nothing to draw */
    public static float word2(Entity e, long c) {
        int d = damage(e);
        if (c == 0 && d == 0) return 0;
        int h = Math.max(1, Math.min(255, Math.round(Math.max(0.1f, e.getBbHeight()) * 10)));
        return (float) (h | d << 8);
    }

    /** the render thread's entity being drawn: its packed coatings (the two 24-bit words) and height (PartBatch's records) */
    static float rtCoat, rtCoat2, rtHeight;

    public static long of(Entity e) {
        ToLongFunction<Entity> p = provider;
        return !ON || p == null || e == null ? 0 : p.applyAsLong(e);
    }

    public static long pack(float wet, float mud, float snow, float ice, float blood, float soot) {
        return q(wet) << WET | q(mud) << MUD | q(snow) << SNOW | q(ice) << ICE | q(blood) << BLOOD | q(soot) << SOOT;
    }

    /** packed coatings with a glow (0..1) of a hue (0..1: red, yellow, green, cyan, blue, magenta round to red) */
    public static long withGlow(long packed, float glow, float hue) {
        long h = Math.floorMod(Math.round(hue * 63), 64);
        return packed & ~(0xFFFL << GLOW) | q(glow) << GLOW | (glow > 0 ? h : 0) << GLOW_HUE;
    }

    private static long q(float v) {
        return Math.max(0, Math.min(63, Math.round(v * 63)));
    }

    /** the first word (wet, mud, snow, ice) and the second (blood, soot, spare), as the records carry them */
    static float word0(long c) {
        return (float) (c & 0xFFFFFF);
    }

    static float word1(long c) {
        return (float) (c >>> 24 & 0xFFFFFF);
    }

    /** the render thread is about to draw e (its packed coatings c, non-zero) */
    public static void begin(Entity e, long c) {
        rtCoat = word0(c);
        rtCoat2 = word1(c);
        rtHeight = word2(e, c);
    }

    public static void end() {
        rtCoat = 0;
        rtCoat2 = 0;
        rtHeight = 0;
    }
}
