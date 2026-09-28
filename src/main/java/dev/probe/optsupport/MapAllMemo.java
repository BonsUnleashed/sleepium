package dev.probe.optsupport;

import java.util.IdentityHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

/**
 * WorldgenShare (mapAll): NoiseChunk builds its density tree with router.mapAll(this::wrap). Named functions
 * (continents, erosion, ridges, …) are referenced through holders from many spline points, and HolderHolder.mapAll /
 * Spline.Coordinate.mapAll re-map the whole referenced tree every time (~20k wrap calls per NoiseChunk, each hashing a
 * record subtree). For one NoiseChunk visitor, mapping the same function object always gives an equal result (wrap
 * canonicalises by equality, nodes are immutable), so the first result is reused for the rest of that traversal.
 * Visitors that are not NoiseChunk's own are never memoised.
 */
public final class MapAllMemo {
    private static final class Session {
        Object visitor;
        final IdentityHashMap<DensityFunction, DensityFunction> map = new IdentityHashMap<>();
    }

    private static final ThreadLocal<Session> SESSION = ThreadLocal.withInitial(Session::new);

    private MapAllMemo() {}

    public static DensityFunction map(DensityFunction f, DensityFunction.Visitor visitor) {
        if (visitor.getClass().getNestHost() != NoiseChunk.class) return f.mapAll(visitor);
        Session s = SESSION.get();
        if (s.visitor != visitor) {
            s.visitor = visitor;
            s.map.clear();
        }
        DensityFunction r = s.map.get(f);
        if (r == null) {
            r = f.mapAll(visitor);
            s.map.put(f, r);
        }
        return r;
    }
}
