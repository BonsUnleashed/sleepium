package dev.lull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Exactness tests (-Dsleep.hashEntitiesDeep): an entity's every instance field by value (the 1.21.1 engine's
 * EntitySleep.deepHash): primitives, value types, synched data, item stacks, lists and plain objects by their fields;
 * identities and per-run randoms left out. Field names are read as Mojang's (Names.official) so dev and production
 * runs skip the same ones.
 */
public final class DeepHash {
    private DeepHash() {}

    private static final Map<Class<?>, Field[]> FIELDS = new HashMap<>();

    public static long of(Entity e) {
        long h = 17;
        for (Field f : fields(e.getClass())) {
            String n = Names.official(f.getName());
            // per run: ids, UUIDs, Math.random() in LivingEntity's constructor (cosmetic)
            if (n.equals("id") || n.equals("uuid") || n.equals("stringUUID") || n.startsWith("sleep$") || n.equals("bobOffs") || n.equals("timeOffs") || n.equals("rotA"))
                continue;
            Class<?> t = f.getType();
            if (!t.isPrimitive() && !(valueType(t) || SynchedEntityData.class.isAssignableFrom(t))) continue;   // (randoms: per run)
            try {
                long v = t == int.class ? f.getInt(e) : t == long.class ? f.getLong(e) : t == boolean.class ? (f.getBoolean(e) ? 1 : 0)
                        : t == short.class ? f.getShort(e) : t == byte.class ? f.getByte(e) : t == float.class ? Float.floatToRawIntBits(f.getFloat(e))
                        : t == double.class ? Double.doubleToRawLongBits(f.getDouble(e)) : t == char.class ? f.getChar(e) : fp(f.get(e), 0);
                h = h * 1_000_003L + n.hashCode() * 31L + v;
            } catch (IllegalAccessException ex) {
                h = h * 31 + 1;
            }
        }
        return h;
    }

    private static boolean valueType(Class<?> t) {
        return t == Vec3.class || t == AABB.class || t == BlockPos.class || t == Boolean.class || t == Integer.class || t == Long.class
                || t == Float.class || t == Double.class || t == Short.class || t == Byte.class || t == String.class || t.isEnum()
                || t == java.util.Optional.class;
    }

    private static long fp(Object r, int depth) {
        if (r instanceof net.minecraft.world.item.ItemStack st)
            return st.isEmpty() ? 0 : BuiltInRegistries.ITEM.getKey(st.getItem()).hashCode() * 1_000_003L + st.getCount() * 7919L + String.valueOf(st.getTag()).hashCode();
        if (r instanceof SynchedEntityData d) {
            long h = d.isDirty() ? 1 : 0;
            List<SynchedEntityData.DataValue<?>> vs = d.getNonDefaultValues();
            if (vs != null) for (SynchedEntityData.DataValue<?> dv : vs) h = h * 31 + dv.id() * 7919L + fp(dv.value(), depth + 1);
            return h;
        }
        if (r instanceof Vec3 || r instanceof AABB || r instanceof BlockPos) return r.toString().hashCode();   // (exact: Double.toString round-trips)
        if (r instanceof Enum<?> en) return en.getDeclaringClass().getName().hashCode() * 31L + en.ordinal();   // Enum.hashCode is per run
        if (r == null) return 0;
        if (depth < 4 && r instanceof Iterable<?> it) {
            long h = 1;
            for (Object o : it) h = h * 31 + fp(o, depth + 1);
            return h;
        }
        if (r instanceof java.util.Optional<?> o) return o.isEmpty() ? 2 : 3 * 31L + fp(o.get(), depth + 1);
        String s = r.toString();
        if (depth < 4 && s.startsWith(r.getClass().getName() + "@")) {   // Object's toString names the identity: by the fields instead
            long h = r.getClass().getName().hashCode();
            for (Field f : fields(r.getClass())) {
                try {
                    Class<?> t = f.getType();
                    long v = t == int.class ? f.getInt(r) : t == long.class ? f.getLong(r) : t == boolean.class ? (f.getBoolean(r) ? 1 : 0)
                            : t == float.class ? Float.floatToRawIntBits(f.getFloat(r)) : t == double.class ? Double.doubleToRawLongBits(f.getDouble(r))
                            : t.isPrimitive() ? String.valueOf(f.get(r)).hashCode() : fp(f.get(r), depth + 1);
                    h = h * 1_000_003L + Names.official(f.getName()).hashCode() * 31L + v;
                } catch (IllegalAccessException ex) {
                    h = h * 31 + 1;
                }
            }
            return h;
        }
        return s.hashCode();
    }

    private static Field[] fields(Class<?> c) {
        return FIELDS.computeIfAbsent(c, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> x = k; x != null && x != Object.class; x = x.getSuperclass())
                for (Field f : x.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getName().equals("sleep$w")) continue;
                    try {
                        f.setAccessible(true);
                        out.add(f);
                    } catch (RuntimeException ignored) {
                    }
                }
            return out.toArray(new Field[0]);
        });
    }
}
