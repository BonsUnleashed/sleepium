package dev.lull.optsupport;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import net.minecraft.core.SectionPos;

/**
 * Lazy walk over the sections of one column (ascending y), for SectionStorageIndexMixin. While the column is complete
 * (every section is in the storage) only sections whose Optional is present are visited: the others would be dropped
 * by vanilla's filter(Optional::isPresent) without side effects. The masks are read live at every step; if the column
 * stops being complete, every remaining y goes through vanilla's getOrLoad, exactly like vanilla.
 */
public final class ColumnSections<R> extends Spliterators.AbstractSpliterator<R> {
    /** Per-column masks kept by the index: [0] = sections stored, [1] = sections with a present value. */
    private final long[] masks;
    private final long full;
    private final int cx, cz, min, n;
    private final LongFunction<Optional<R>> getOrLoad;
    private int y;

    public ColumnSections(long[] masks, long full, int cx, int cz, int min, int n, LongFunction<Optional<R>> getOrLoad) {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        this.masks = masks;
        this.full = full;
        this.cx = cx;
        this.cz = cz;
        this.min = min;
        this.n = n;
        this.getOrLoad = getOrLoad;
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        while (y < n) {
            int i = y;
            if (masks[0] == full) {
                long bits = masks[1] & (-1L << i);
                if (bits == 0) {
                    y = n;
                    return false;
                }
                i = Long.numberOfTrailingZeros(bits);
                if (i >= n) {
                    y = n;
                    return false;
                }
            }
            y = i + 1;
            Optional<R> o = getOrLoad.apply(SectionPos.asLong(cx, min + i, cz));
            if (o.isPresent()) {
                action.accept(o.get());
                return true;
            }
        }
        return false;
    }
}
