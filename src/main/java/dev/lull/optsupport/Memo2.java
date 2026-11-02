package dev.lull.optsupport;

import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * UtilMemoizeMixin: Util.memoize(BiFunction) without a Pair key and a capturing lambda per call (RenderType
 * .entityCutoutNoCull(texture) and friends are asked per object per frame). Two levels of ConcurrentHashMap: a hit
 * allocates nothing; a miss computes once per (first, second) as vanilla's computeIfAbsent does. Null arguments (a Pair
 * may hold them, a ConcurrentHashMap key may not) use vanilla's Pair-keyed map.
 */
public final class Memo2<T, U, R> implements BiFunction<T, U, R> {
    private final BiFunction<T, U, R> fn;
    private final ConcurrentHashMap<T, ConcurrentHashMap<U, R>> cache = new ConcurrentHashMap<>();
    private final Map<Pair<T, U>, R> nulls = new ConcurrentHashMap<>();
    @SuppressWarnings("rawtypes")
    private static final Function NEW_INNER = k -> new ConcurrentHashMap<>();

    public Memo2(BiFunction<T, U, R> fn) {
        this.fn = fn;
    }

    @Override
    @SuppressWarnings("unchecked")
    public R apply(T t, U u) {
        if (t == null || u == null) return nulls.computeIfAbsent(Pair.of(t, u), p -> fn.apply(p.getFirst(), p.getSecond()));
        ConcurrentHashMap<U, R> inner = cache.get(t);
        if (inner == null) inner = cache.computeIfAbsent(t, (Function<T, ConcurrentHashMap<U, R>>) NEW_INNER);
        R r = inner.get(u);
        if (r != null) return r;
        return inner.computeIfAbsent(u, k -> fn.apply(t, k));
    }

    @Override
    public String toString() {
        int size = nulls.size();
        for (var m : cache.values()) size += m.size();
        return "memoize/2[function=" + fn + ", size=" + size + "]";
    }
}
