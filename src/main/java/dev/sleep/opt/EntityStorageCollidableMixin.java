package dev.sleep.opt;

import dev.sleep.optsupport.CollidableCount;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Goes with EntityCollisionSkipMixin: an existence query over exactly the sections vanilla's
 * forEachAccessibleNonEmptySection would visit for the box (order is irrelevant for "any").
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntityStorageCollidableMixin implements CollidableCount.Storage {
    @Shadow @Final private Long2ObjectMap<EntitySection<?>> sections;
    @Shadow @Final private LongSortedSet sectionIds;

    @Override
    public boolean sleep$anyCollidable(AABB bb) {
        int x0 = SectionPos.posToSectionCoord(bb.minX - 2.0);
        int y0 = SectionPos.posToSectionCoord(bb.minY - 4.0);
        int z0 = SectionPos.posToSectionCoord(bb.minZ - 2.0);
        int x1 = SectionPos.posToSectionCoord(bb.maxX + 2.0);
        int y1 = SectionPos.posToSectionCoord(bb.maxY + 0.0);
        int z1 = SectionPos.posToSectionCoord(bb.maxZ + 2.0);
        long n = (x1 - x0 + 1L) * (y1 - y0 + 1L) * (z1 - z0 + 1L);
        if (x1 < x0 || y1 < y0 || z1 < z0) return false;
        if (n <= 64 || n <= sections.size()) {
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++)
                    for (int y = y0; y <= y1; y++)
                        if (sleep$counts(sections.get(SectionPos.asLong(x, y, z)))) return true;
            return false;
        }
        for (int x = x0; x <= x1; x++) {   // vanilla walk: huge box over a sparse map
            LongIterator it = sectionIds.subSet(SectionPos.asLong(x, 0, 0), SectionPos.asLong(x, -1, -1) + 1L).iterator();
            while (it.hasNext()) {
                long k = it.nextLong();
                int y = SectionPos.y(k), z = SectionPos.z(k);
                if (y >= y0 && y <= y1 && z >= z0 && z <= z1 && sleep$counts(sections.get(k))) return true;
            }
        }
        return false;
    }

    private static boolean sleep$counts(EntitySection<?> s) {
        return s != null && ((CollidableCount) s).sleep$collidable() > 0 && !s.isEmpty() && s.getStatus().isAccessible();
    }
}
