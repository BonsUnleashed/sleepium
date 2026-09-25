package dev.probe.optsupport;

import java.util.concurrent.atomic.AtomicLong;

/** Counters of opt mixins (mixin classes cannot be referenced from normal code, so they live here). */
public final class OptCounters {
    /** EntitySectionScanMixin shadow check: queries compared with the vanilla walk, and differences found. */
    public static final AtomicLong SECTION_CHECKED = new AtomicLong(), SECTION_MISMATCHES = new AtomicLong();
    /** EntitySectionScanMixin: queries served by the direct walk / by the vanilla fallback (huge boxes). */
    public static final AtomicLong SECTION_FAST = new AtomicLong(), SECTION_FALLBACK = new AtomicLong();

    /** ChunkCacheMixin shadow check: cache hits compared with vanilla getChunk, and differences. */
    public static final AtomicLong CHUNK_CHECKED = new AtomicLong(), CHUNK_MISMATCHES = new AtomicLong();

    /** section index shadow check */
    public static final AtomicLong INDEX_CHECKED = new AtomicLong(), INDEX_MISMATCHES = new AtomicLong();

    private OptCounters() {}

    public static String report() {
        return String.format(java.util.Locale.ROOT, "  -- opt counters: section scan fast %d, fallback %d, shadow-checked %d, MISMATCHES %d; chunk cache checked %d, MISMATCHES %d; section index checked %d, MISMATCHES %d (rebuilds %d, updates %d, queries %d)%n",
                SECTION_FAST.get(), SECTION_FALLBACK.get(), SECTION_CHECKED.get(), SECTION_MISMATCHES.get(), CHUNK_CHECKED.get(), CHUNK_MISMATCHES.get(), INDEX_CHECKED.get(), INDEX_MISMATCHES.get(), SectionIndex.rebuilds, SectionIndex.updates, SectionIndex.queries);
    }
}
