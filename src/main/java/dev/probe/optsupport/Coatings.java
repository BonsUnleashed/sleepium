package dev.probe.optsupport;

import java.util.function.ToIntFunction;
import net.minecraft.world.entity.Entity;

/**
 * Surface coatings on entities (client): what lies on a body and what it wears - wet, mud, snow, ice - drawn by the
 * material shading (Materials) over the textures, so armour takes them as the body does. A game registers
 * {@link #provider}: an entity's coatings packed in an int, 6 bits each (0..63): wet | mud << 6 | snow << 12 | ice << 18;
 * 0 is none. The amounts reach the shader through PartBatch's per-draw records (the normal matrix columns' spare w:
 * n0.w the packed amounts, n2.w the entity's height), so only part-batched geometry (vanilla parts, GeckoLib bones)
 * shows them, as with materials. An entity with a coating is drawn live, not from the render cache (its recorded
 * vertices carry no records); one without draws exactly as before (the shader's branch is off).
 * {@code -Dprobe.coatings=false} turns them off.
 */
public final class Coatings {
    private Coatings() {}

    public static final boolean ON = !"false".equals(System.getProperty("probe.coatings"));
    public static final int WET = 0, MUD = 6, SNOW = 12, ICE = 18;

    /** the game's: an entity's packed coatings (render thread or a ParallelRender worker; must not touch the world) */
    public static volatile ToIntFunction<Entity> provider;

    /** the render thread's entity being drawn: its packed coatings and height (PartBatch's records) */
    static float rtCoat, rtHeight;

    public static int of(Entity e) {
        ToIntFunction<Entity> p = provider;
        return !ON || p == null || e == null ? 0 : p.applyAsInt(e);
    }

    public static int pack(float wet, float mud, float snow, float ice) {
        return q(wet) << WET | q(mud) << MUD | q(snow) << SNOW | q(ice) << ICE;
    }

    private static int q(float v) {
        return Math.max(0, Math.min(63, Math.round(v * 63)));
    }

    /** the render thread is about to draw e (its packed coatings c, non-zero) */
    public static void begin(Entity e, int c) {
        rtCoat = c;
        rtHeight = c == 0 ? 0 : Math.max(0.1f, e.getBbHeight());
    }

    public static void end() {
        rtCoat = 0;
        rtHeight = 0;
    }
}
