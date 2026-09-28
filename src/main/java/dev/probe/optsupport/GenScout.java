package dev.probe.optsupport;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measurement only (-Dlab.genscout=true): which per-block paths NOISE takes. Aquifer.computeSubstance by input
 * (solid: substance > 0, returns null at once) and by result for the rest; Beardifier: structure pieces and junctions
 * each chunk's per-block compute walks.
 */
public final class GenScout {
    public static final LongAdder AQ_SOLID = new LongAdder(), AQ_NULL = new LongAdder(), AQ_AIR = new LongAdder(),
            AQ_WATER = new LongAdder(), AQ_LAVA = new LongAdder(), AQ_OTHER = new LongAdder(), AQ_HIGH = new LongAdder();
    public static final LongAdder BEARD_CHUNKS = new LongAdder(), BEARD_NONEMPTY = new LongAdder(),
            BEARD_RIGIDS = new LongAdder(), BEARD_JUNCTIONS = new LongAdder(), BEARD_CALLS = new LongAdder();
    public static final LongAdder FLUID_CALLS = new LongAdder(), FLUID_NANOS = new LongAdder(), SURF_CALLS = new LongAdder(), SURF_NANOS = new LongAdder();

    public static final boolean ENABLED = Boolean.getBoolean("lab.genscout");
    /** off while perlinBench runs, so the bench times the bare call */
    public static boolean counting = true;
    private static final ThreadLocal<long[]> PERLIN = ThreadLocal.withInitial(() -> new long[1]);

    private GenScout() {}

    /** ImprovedNoise.noise HEAD (GenScoutPerlinMixin) */
    public static void perlin() {
        if (counting) PERLIN.get()[0]++;
    }

    /** this thread's ImprovedNoise.noise calls so far (0 without the scout) */
    public static long perlinCount() {
        return ENABLED ? PERLIN.get()[0] : 0;
    }

    /** ns per ImprovedNoise.noise call, warm, plain and with a y scale (BlendedNoise's form) */
    public static String perlinBench() {
        counting = false;
        try {
            net.minecraft.world.level.levelgen.synth.ImprovedNoise n =
                    new net.minecraft.world.level.levelgen.synth.ImprovedNoise(net.minecraft.util.RandomSource.create(42L));
            double sink = 0;
            long best3 = Long.MAX_VALUE, best5 = Long.MAX_VALUE;
            final int N = 2_000_000;
            for (int round = 0; round < 6; round++) {
                long t = System.nanoTime();
                for (int i = 0; i < N; i++) sink += n.noise(i * 0.371, (i % 384) * 0.131, i * 0.713);
                best3 = Math.min(best3, System.nanoTime() - t);
                t = System.nanoTime();
                for (int i = 0; i < N; i++) sink += n.noise(i * 0.371, (i % 384) * 0.131, i * 0.713, 0.25, 0.5);
                best5 = Math.min(best5, System.nanoTime() - t);
            }
            return String.format(Locale.ROOT, "perlin bench: ImprovedNoise.noise %.1f ns/call, with y scale %.1f ns/call (sink %.3f)",
                    (double) best3 / N, (double) best5 / N, sink);
        } finally {
            counting = true;
        }
    }

    public static String reportAndReset() {
        long solid = AQ_SOLID.sumThenReset(), nul = AQ_NULL.sumThenReset(), air = AQ_AIR.sumThenReset(), water = AQ_WATER.sumThenReset(),
                lava = AQ_LAVA.sumThenReset(), other = AQ_OTHER.sumThenReset(), high = AQ_HIGH.sumThenReset();
        long all = Math.max(1, solid + nul + air + water + lava + other);
        long ch = BEARD_CHUNKS.sumThenReset(), ne = BEARD_NONEMPTY.sumThenReset(), r = BEARD_RIGIDS.sumThenReset(),
                j = BEARD_JUNCTIONS.sumThenReset(), bc = BEARD_CALLS.sumThenReset();
        long fc = FLUID_CALLS.sumThenReset(), fn = FLUID_NANOS.sumThenReset(), sc = SURF_CALLS.sumThenReset(), sn = SURF_NANOS.sumThenReset();
        return String.format(Locale.ROOT, "gen scout: aquifer computeFluid %d calls %.1f ms total; preliminary surface columns computed %d, %.1f ms total%n", fc, fn / 1e6, sc, sn / 1e6)
                + String.format(Locale.ROOT,
                "gen scout: aquifer %d calls: solid %.1f %%, slow path %.1f %% (-> null/barrier %.1f %%, air %.1f %%, water %.1f %%, lava %.1f %%, other %.1f %%; slow path at y >= 63: %.1f %% of all)%n"
                        + "  beardifier: %d NoiseChunks, %d with pieces; %.1f rigid pieces + %.1f junctions per non-empty chunk; %d per-block calls",
                all, 100.0 * solid / all, 100.0 * (all - solid) / all, 100.0 * nul / all, 100.0 * air / all, 100.0 * water / all,
                100.0 * lava / all, 100.0 * other / all, 100.0 * high / all, ch, ne, ne == 0 ? 0 : (double) r / ne, ne == 0 ? 0 : (double) j / ne, bc);
    }
}
