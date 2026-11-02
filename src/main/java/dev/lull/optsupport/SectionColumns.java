package dev.lull.optsupport;

import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;

/** Implemented by SectionStorage through SectionStorageIndexMixin. */
public interface SectionColumns<R> {
    /** The present sections of a column in ascending y, lazily, with vanilla getInChunk semantics. */
    Stream<R> sleep$sectionsInColumn(ChunkPos pos);
}
