package dev.lull.opt;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.20.1 (gone in 1.20.5): every read of an entity's synched data takes a ReentrantReadWriteLock's read lock -
 * isOnFire, hasCustomName, isCurrentlyGlowing, ItemEntity.getItem..., a few per entity per tick and per frame
 * (client_entities: 5 % of the render thread in the lock alone). The map it guards only changes when a data item is
 * defined (Entity's constructor, under the write lock); reading a value never changes it, so the read without the lock
 * returns the same item. 1.20.5 removed the lock (an array per entity). The crash report on a failed read is kept.
 */
@Mixin(SynchedEntityData.class)
public abstract class EntityDataNoLockMixin {
    @Shadow @Final private Int2ObjectMap<SynchedEntityData.DataItem<?>> itemsById;

    /**
     * @author sleep
     * @reason the read lock around a lookup in a map that is no longer written to (see the class comment)
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    private <T> SynchedEntityData.DataItem<T> getItem(EntityDataAccessor<T> key) {
        try {
            return (SynchedEntityData.DataItem<T>) itemsById.get(key.getId());
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting synched entity data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Synched entity data");
            crashreportcategory.setDetail("Data ID", key);
            throw new ReportedException(crashreport);
        }
    }
}
