package dev.probe.opt;

import dev.probe.optsupport.ColumnSections;
import dev.probe.optsupport.OptCounters;
import dev.probe.optsupport.SectionColumns;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * exp/poi-columns (goes with PoiColumnMixin): a per-column index of SectionStorage (the POI storage): which sections
 * are stored and which hold a value. The storage map is only changed by three put calls (getOrCreate, readColumn) and
 * NeoForge's remove; all of them are redirected through the index. Columns taller than 64 sections are not indexed
 * (vanilla path).
 */
@Mixin(SectionStorage.class)
public abstract class SectionStorageIndexMixin<R> implements SectionColumns<R> {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @Shadow protected abstract Optional<R> getOrLoad(long sectionKey);

    @Unique private static final boolean CHECK = Boolean.getBoolean("lab.opt.poiCheck");
    /** column (ChunkPos long) -> {stored mask, present mask} */
    @Unique private final Long2ObjectOpenHashMap<long[]> probe$cols = new Long2ObjectOpenHashMap<>();

    @Redirect(method = {"getOrCreate", "readColumn(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/nbt/CompoundTag;)V"},
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private Object probe$put(Long2ObjectMap<Object> map, long key, Object value) {
        Object prev = map.put(key, value);
        int n = levelHeightAccessor.getSectionsCount();
        int bit = SectionPos.y(key) - levelHeightAccessor.getMinSection();
        if (n <= 64 && bit >= 0 && bit < n) {
            long col = ChunkPos.asLong(SectionPos.x(key), SectionPos.z(key));
            long[] m = probe$cols.get(col);
            if (m == null) probe$cols.put(col, m = new long[2]);
            m[0] |= 1L << bit;
            if (((Optional<?>) value).isPresent()) m[1] |= 1L << bit;
            else m[1] &= ~(1L << bit);
        }
        return prev;
    }

    @Redirect(method = "remove(J)V", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;remove(J)Ljava/lang/Object;"))
    private Object probe$remove(Long2ObjectMap<Object> map, long key) {
        Object prev = map.remove(key);
        int bit = SectionPos.y(key) - levelHeightAccessor.getMinSection();
        long col = ChunkPos.asLong(SectionPos.x(key), SectionPos.z(key));
        long[] m = probe$cols.get(col);
        if (m != null && bit >= 0 && bit < 64) {
            m[0] &= ~(1L << bit);
            m[1] &= ~(1L << bit);
            if (m[0] == 0) probe$cols.remove(col);
        }
        return prev;
    }

    @Override
    public Stream<R> probe$sectionsInColumn(ChunkPos pos) {
        int min = levelHeightAccessor.getMinSection(), n = levelHeightAccessor.getSectionsCount();
        long[] m = n <= 64 ? probe$cols.get(pos.toLong()) : null;
        long full = n == 64 ? -1L : (1L << n) - 1;
        if (m == null || m[0] != full) {
            // column not (fully) loaded: vanilla, which reads it from disk on the first missing section
            return IntStream.range(min, levelHeightAccessor.getMaxSection()).boxed()
                    .map(y -> getOrLoad(SectionPos.of(pos, y).asLong())).filter(Optional::isPresent).map(Optional::get);
        }
        if (CHECK) probe$check(pos, m, full, min, n);
        if (m[1] == 0) return Stream.empty();
        return StreamSupport.stream(new ColumnSections<>(m, full, pos.x, pos.z, min, n, this::getOrLoad), false);
    }

    /** Shadow check (complete columns only, so vanilla's walk has no side effects): same sections, same order. */
    @Unique
    private void probe$check(ChunkPos pos, long[] m, long full, int min, int n) {
        List<Object> a = new ArrayList<>(), b = new ArrayList<>();
        for (int y = min; y < min + n; y++) getOrLoad(SectionPos.of(pos, y).asLong()).ifPresent(a::add);
        StreamSupport.stream(new ColumnSections<>(m, full, pos.x, pos.z, min, n, this::getOrLoad), false).forEach(b::add);
        OptCounters.POI_CHECKED.incrementAndGet();
        boolean same = a.size() == b.size();
        for (int i = 0; same && i < a.size(); i++) same = a.get(i) == b.get(i);
        if (!same && OptCounters.POI_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] POI column MISMATCH at " + pos + ": vanilla " + a.size() + " vs index " + b.size());
    }
}
