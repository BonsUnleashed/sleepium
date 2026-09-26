package dev.probe.optsupport.sleep;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Value fingerprints for the entity sleep snapshot. An entity replaces its vectors and boxes with new, equal objects
 * every tick, so they are compared by value; its random generator by seed (a draw changes the state); synched data by
 * the values and dirty flags of its items. Everything else as {@link dev.probe.FieldDiff#fingerprint}.
 */
public final class EntityFp {
    private static final VarHandle ITEMS, DIRTY, ITEM_DIRTY, LEGACY_SEED, SINGLE_SEED;

    static {
        VarHandle items = null, dirty = null, itemDirty = null, legacy = null, single = null;
        try {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(SynchedEntityData.class, MethodHandles.lookup());
            items = l.findVarHandle(SynchedEntityData.class, "itemsById", SynchedEntityData.DataItem[].class);
            dirty = l.findVarHandle(SynchedEntityData.class, "isDirty", boolean.class);
            itemDirty = MethodHandles.privateLookupIn(SynchedEntityData.DataItem.class, MethodHandles.lookup())
                    .findVarHandle(SynchedEntityData.DataItem.class, "dirty", boolean.class);
            Class<?> lr = net.minecraft.world.level.levelgen.LegacyRandomSource.class;
            legacy = MethodHandles.privateLookupIn(lr, MethodHandles.lookup()).findVarHandle(lr, "seed", java.util.concurrent.atomic.AtomicLong.class);
            Class<?> sr = net.minecraft.world.level.levelgen.SingleThreadedRandomSource.class;
            single = MethodHandles.privateLookupIn(sr, MethodHandles.lookup()).findVarHandle(sr, "seed", long.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[probe/entsleep] fingerprint handles unavailable: " + e);
        }
        ITEMS = items;
        DIRTY = dirty;
        ITEM_DIRTY = itemDirty;
        LEGACY_SEED = legacy;
        SINGLE_SEED = single;
    }

    private EntityFp() {}

    static boolean available() {
        return ITEMS != null && DIRTY != null && ITEM_DIRTY != null && LEGACY_SEED != null && SINGLE_SEED != null;
    }

    public static long fp(Object r) {
        if (r == null) return 0;
        if (r instanceof Vec3 v) return mix(mix(bits(v.x), bits(v.y)), bits(v.z));
        if (r instanceof AABB b) return mix(mix(mix(mix(mix(bits(b.minX), bits(b.minY)), bits(b.minZ)), bits(b.maxX)), bits(b.maxY)), bits(b.maxZ));
        if (r instanceof BlockPos p) return p.asLong() * 0x9E3779B97F4A7C15L + 1;
        if (r instanceof SynchedEntityData d) return synched(d);
        if (r instanceof net.minecraft.world.level.levelgen.LegacyRandomSource) return ((java.util.concurrent.atomic.AtomicLong) LEGACY_SEED.get(r)).get() * 31 + 7;
        if (r instanceof net.minecraft.world.level.levelgen.SingleThreadedRandomSource) return (long) SINGLE_SEED.get(r) * 31 + 11;
        if (r instanceof Boolean || r instanceof Number || r instanceof String || r instanceof Enum<?>) return r.hashCode() * 31L + 3;
        return dev.probe.FieldDiff.fingerprint(r);
    }

    private static long synched(SynchedEntityData d) {
        long h = (boolean) DIRTY.get(d) ? 1 : 0;
        for (SynchedEntityData.DataItem<?> it : (SynchedEntityData.DataItem<?>[]) ITEMS.get(d)) {
            if (it == null) continue;
            Object v = it.getValue();
            long x = v instanceof ItemStack || v instanceof Vec3 || v instanceof BlockPos ? fp(v) : java.util.Objects.hashCode(v);
            h = mix(h, x * 2 + ((boolean) ITEM_DIRTY.get(it) ? 1 : 0));
        }
        return h;
    }

    private static long bits(double d) {
        return Double.doubleToRawLongBits(d);
    }

    private static long mix(long h, long x) {
        return (h ^ x) * 0x100000001B3L + (x >>> 29);
    }
}
