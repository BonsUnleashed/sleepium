package dev.probe.optsupport;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * WorldgenAlloc (randoms): PositionalRandomFactory.at(x, y, z) builds a new XoroshiroRandomSource (three objects) for
 * one nextFloat/nextDouble at call sites that never keep it — surface gradients (bedrock, deepslate) per block, ore veins
 * per vein block, surface depth per column. One instance per thread and call site, reset to exactly the state the
 * constructor would give (Xoroshiro128PlusPlus's zero-seed replacement included, the gaussian's cached value dropped).
 * Factories other than Xoroshiro's are called as usual.
 */
public final class ReusableRandom {
    public static final int GRADIENT = 0, ORE = 1, DEPTH = 2;

    /** implemented by accessor mixins */
    public interface Factory {
        long probe$seedLo();

        long probe$seedHi();
    }

    public interface Source {
        net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus probe$rng();

        net.minecraft.world.level.levelgen.MarsagliaPolarGaussian probe$gaussian();
    }

    public interface State {
        void probe$setLo(long v);

        void probe$setHi(long v);
    }

    private static final ThreadLocal<XoroshiroRandomSource[]> SLOTS = ThreadLocal.withInitial(() -> new XoroshiroRandomSource[3]);

    private ReusableRandom() {}

    public static RandomSource at(PositionalRandomFactory f, int x, int y, int z, int slot) {
        if (!(f instanceof XoroshiroRandomSource.XoroshiroPositionalRandomFactory) || !(f instanceof Factory fa)) return f.at(x, y, z);
        long lo = Mth.getSeed(x, y, z) ^ fa.probe$seedLo(), hi = fa.probe$seedHi();
        XoroshiroRandomSource[] a = SLOTS.get();
        XoroshiroRandomSource r = a[slot];
        if (r == null) return a[slot] = new XoroshiroRandomSource(lo, hi);
        if ((lo | hi) == 0L) {   // as Xoroshiro128PlusPlus(long, long)
            lo = -7046029254386353131L;
            hi = 7640891576956012809L;
        }
        Source s = (Source) r;
        State st = (State) (Object) s.probe$rng();
        st.probe$setLo(lo);
        st.probe$setHi(hi);
        s.probe$gaussian().reset();
        return r;
    }
}
