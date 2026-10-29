package dev.probe.optsupport;

import java.util.concurrent.atomic.AtomicLong;

/** Counters of opt mixins (mixin classes cannot be referenced from normal code, so they live here). */
public final class OptCounters {
    /** CubeAt collides (kept here: reading it must not initialise CubeAt, which needs the CubeShapeMixin accessor) */
    public static long cubeCollides;
    /** EntitySectionScanMixin shadow check: queries compared with the vanilla walk, and differences found. */
    public static final AtomicLong SECTION_CHECKED = new AtomicLong(), SECTION_MISMATCHES = new AtomicLong();
    /** EntitySectionScanMixin: queries served by the direct walk / by the vanilla fallback (huge boxes). */
    public static final AtomicLong SECTION_FAST = new AtomicLong(), SECTION_FALLBACK = new AtomicLong();

    /** ChunkCacheMixin shadow check: cache hits compared with vanilla getChunk, and differences. */
    public static final AtomicLong CHUNK_CHECKED = new AtomicLong(), CHUNK_MISMATCHES = new AtomicLong();

    /** section index shadow check */
    public static final AtomicLong INDEX_CHECKED = new AtomicLong(), INDEX_MISMATCHES = new AtomicLong();

    /** BrainFlatMixin: flat-array rebuilds, shadow-checked ticks, order differences, mid-tick mutations resumed */
    public static final AtomicLong BRAIN_REBUILDS = new AtomicLong(), BRAIN_CHECKED = new AtomicLong(), BRAIN_MISMATCHES = new AtomicLong(),
            BRAIN_RESUMES = new AtomicLong();

    /** PoiColumnMixin shadow check: complete columns compared with the vanilla section walk */
    public static final AtomicLong POI_CHECKED = new AtomicLong(), POI_MISMATCHES = new AtomicLong();

    /** ShapeCacheMixin shadow check: cached shape vs vanilla call (identity) */
    public static final AtomicLong SHAPE_CHECKED = new AtomicLong(), SHAPE_MISMATCHES = new AtomicLong();

    /** EntityCollisionSkipMixin shadow check: skipped queries re-run through vanilla, and non-empty answers */
    public static final AtomicLong COLLIDE_CHECKED = new AtomicLong(), COLLIDE_MISMATCHES = new AtomicLong();

    /** BlockCollisionMemoMixin shadow check */
    public static final AtomicLong MEMO_CHECKED = new AtomicLong(), MEMO_MISMATCHES = new AtomicLong();

    /** SignalPathMixin shadow check */
    public static final AtomicLong SIGNAL_CHECKED = new AtomicLong(), SIGNAL_MISMATCHES = new AtomicLong();

    /** CubeShapeMixin shadow check */
    public static final AtomicLong CUBE_CHECKED = new AtomicLong(), CUBE_MISMATCHES = new AtomicLong();

    /** TickSchedulerMixin shadow check */
    public static final AtomicLong TICKSCHED_CHECKED = new AtomicLong(), TICKSCHED_MISMATCHES = new AtomicLong();

    private OptCounters() {}

    public static String report() {
        return String.format(java.util.Locale.ROOT, "  -- opt counters: section scan fast %d, fallback %d, shadow-checked %d, MISMATCHES %d; chunk cache checked %d, MISMATCHES %d; section index checked %d, MISMATCHES %d (rebuilds %d, updates %d, queries %d); brain rebuilds %d, checked %d, MISMATCHES %d, resumes %d; poi columns checked %d, MISMATCHES %d; shapes checked %d, MISMATCHES %d (static classes %d, vanilla %d); collide skips checked %d, MISMATCHES %d; collision memo hits %d, misses %d, unmemoizable %d, collide %d/%d, support %d/%d, checked %d, MISMATCHES %d; %s; signal checked %d, MISMATCHES %d, %s; cube collides %d, checked %d, MISMATCHES %d; tick scheduler checked %d, MISMATCHES %d; %s%n",
                SECTION_FAST.get(), SECTION_FALLBACK.get(), SECTION_CHECKED.get(), SECTION_MISMATCHES.get(), CHUNK_CHECKED.get(), CHUNK_MISMATCHES.get(), INDEX_CHECKED.get(), INDEX_MISMATCHES.get(), SectionIndex.rebuilds, SectionIndex.updates, SectionIndex.queries,
                BRAIN_REBUILDS.get(), BRAIN_CHECKED.get(), BRAIN_MISMATCHES.get(), BRAIN_RESUMES.get(), POI_CHECKED.get(), POI_MISMATCHES.get(),
                SHAPE_CHECKED.get(), SHAPE_MISMATCHES.get(), ShapePurity.accepted, ShapePurity.rejected,
                COLLIDE_CHECKED.get(), COLLIDE_MISMATCHES.get(),
                BlockCollisionMemo.hits, BlockCollisionMemo.misses, BlockCollisionMemo.unmemoizable, BlockCollisionMemo.collideHits, BlockCollisionMemo.collideMisses,
                BlockCollisionMemo.supportHits, BlockCollisionMemo.supportMisses, MEMO_CHECKED.get(), MEMO_MISMATCHES.get(),
                EventSites.report(), SIGNAL_CHECKED.get(), SIGNAL_MISMATCHES.get(), SignalProof.report(),
                cubeCollides, CUBE_CHECKED.get(), CUBE_MISMATCHES.get(), TICKSCHED_CHECKED.get(), TICKSCHED_MISMATCHES.get(), JobSiteIndex.report());
    }
}
