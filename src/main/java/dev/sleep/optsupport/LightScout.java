package dev.sleep.optsupport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Measurement only (-Dlab.lightscout=true): could a section's light come from a light volume in the shader instead
 * of a re-mesh? Per compiled section, its quads by lighting path: AO full faces (a 4-sample integer blend: a recipe
 * fits a vertex), AO partial faces (weighted float blend: stays baked), flat faces (one sample), fluids (own light
 * code). A section is "volume-eligible" when it has no partial AO face and no fluid.
 */
public final class LightScout {
    private LightScout() {}

    public static final boolean ON = Boolean.getBoolean("lab.lightscout");
    private static final ThreadLocal<long[]> CUR = ThreadLocal.withInitial(() -> new long[4]);
    public static final AtomicLong sections = new AtomicLong(), eligible = new AtomicLong(), emptySections = new AtomicLong();
    public static final AtomicLong full = new AtomicLong(), partial = new AtomicLong(), flat = new AtomicLong(), fluid = new AtomicLong(),
            eligibleQuads = new AtomicLong();

    public static void begin() {
        long[] c = CUR.get();
        c[0] = c[1] = c[2] = c[3] = 0;
    }

    public static void aoFace(boolean isPartial) {
        CUR.get()[isPartial ? 1 : 0]++;
    }

    public static void flatFace() {
        CUR.get()[2]++;
    }

    public static void fluidBlock() {
        CUR.get()[3]++;
    }

    public static void end() {
        long[] c = CUR.get();
        long quads = c[0] + c[1] + c[2];
        if (quads + c[3] == 0) {
            emptySections.incrementAndGet();
            return;
        }
        sections.incrementAndGet();
        full.addAndGet(c[0]);
        partial.addAndGet(c[1]);
        flat.addAndGet(c[2]);
        fluid.addAndGet(c[3]);
        if (c[1] == 0 && c[3] == 0) {
            eligible.incrementAndGet();
            eligibleQuads.addAndGet(quads);
        }
    }

    public static String report() {
        long s = Math.max(1, sections.get()), q = Math.max(1, full.get() + partial.get() + flat.get());
        return String.format(java.util.Locale.ROOT,
                "light scout: %d sections compiled (+%d empty), volume-eligible %.1f %% of them (%.1f %% of quads); quads: AO full %.1f %%, AO partial %.1f %%, flat %.1f %%; fluid blocks %d",
                sections.get(), emptySections.get(), 100.0 * eligible.get() / s, 100.0 * eligibleQuads.get() / q, 100.0 * full.get() / q, 100.0 * partial.get() / q,
                100.0 * flat.get() / q, fluid.get());
    }
}
