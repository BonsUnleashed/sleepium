package dev.sleep.opt;

import dev.sleep.optsupport.SectionColumns;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * exp/poi-columns: every POI range query (villager jobs, beds and meeting points, bees, portals, lightning rods,
 * lodestones, modded POI types) goes through getInChunk for each chunk of the square: vanilla boxes 24 section
 * indices, builds a SectionPos and does a hash lookup per section, then filters Optionals, almost all empty.
 * Here loaded columns only visit the sections that hold POI data (SectionStorageIndexMixin), lazily, in the same
 * order; the records stream per section is vanilla's own.
 */
@Mixin(PoiManager.class)
public abstract class PoiColumnMixin {
    /**
     * @author probe (lab.opt.PoiColumnMixin)
     * @reason skip sections without POI data in loaded columns; same records, same order, same laziness
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos posChunk, PoiManager.Occupancy status) {
        return ((SectionColumns<PoiSection>) (Object) this).probe$sectionsInColumn(posChunk).flatMap(s -> s.getRecords(typePredicate, status));
    }
}
