package dev.probe.optsupport;

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
 * {@code -Dprobe.coatings=false} turns them off.
 */
public final class Coatings {
    private Coatings() {}

    public static final boolean ON = !"false".equals(System.getProperty("probe.coatings"));
    public static final int WET = 0, MUD = 6, SNOW = 12, ICE = 18, BLOOD = 24, SOOT = 30, GLOW = 36, GLOW_HUE = 42;

    /** the game's: an entity's packed coatings (render thread or a ParallelRender worker; must not touch the world) */
    public static volatile ToLongFunction<Entity> provider;

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
        rtHeight = c == 0 ? 0 : Math.max(0.1f, e.getBbHeight());
    }

    public static void end() {
        rtCoat = 0;
        rtCoat2 = 0;
        rtHeight = 0;
    }
}
