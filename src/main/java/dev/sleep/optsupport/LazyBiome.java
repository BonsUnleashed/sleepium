package dev.sleep.optsupport;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * WorldgenAlloc: SurfaceRules.Context.updateY builds `Suppliers.memoize(() -> biomeGetter.apply(pos.set(x, y, z)))`
 * for every block of every column (a lambda plus a memoizing supplier, ~21 % of worldgen allocation). The supplier is
 * only read through context.biome before the next updateY, so one reusable instance per Context, reset per Y, does
 * the same thing: the same lazy lookup with the same coordinates, at most once per Y.
 */
public final class LazyBiome implements Supplier<Holder<Biome>> {
    private final Function<BlockPos, Holder<Biome>> getter;
    private final BlockPos.MutableBlockPos pos;
    private int x, y, z;
    private Holder<Biome> value;

    public LazyBiome(Function<BlockPos, Holder<Biome>> getter, BlockPos.MutableBlockPos pos) {
        this.getter = getter;
        this.pos = pos;
    }

    public LazyBiome reset(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        value = null;
        return this;
    }

    @Override
    public Holder<Biome> get() {
        Holder<Biome> v = value;
        if (v == null) value = v = getter.apply(pos.set(x, y, z));
        return v;
    }
}
