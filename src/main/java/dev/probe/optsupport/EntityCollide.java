package dev.probe.optsupport;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.entity.Entity;

/**
 * Class-level facts for EntityCollisionSkipMixin, read by reflection from the loaded (already transformed) classes, so
 * overrides added by any mod or mixin are seen:
 * <ul>
 *   <li>{@link #CAPABLE}: canBeCollidedWith() is declared somewhere below Entity (Entity's own returns false);</li>
 *   <li>{@link #DEFAULT_COLLIDE}: canCollideWith(Entity) is Entity's own: other.canBeCollidedWith() && ...</li>
 * </ul>
 * {@link #ENTITY_UNTOUCHED}: no mixin applied to Entity mentions either method (their bodies are then vanilla's).
 */
public final class EntityCollide {
    public static final ClassValue<Boolean> CAPABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("canBeCollidedWith").getDeclaringClass() != Entity.class;
            } catch (Throwable t) {
                return true;
            }
        }
    };

    public static final ClassValue<Boolean> DEFAULT_COLLIDE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("canCollideWith", Entity.class).getDeclaringClass() == Entity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    public static final boolean ENTITY_UNTOUCHED = !mixinsMention(Entity.class, "canBeCollidedWith", "canCollideWith");

    public static volatile long skipped, full;

    private EntityCollide() {}

    /** Whether any mixin applied to {@code target} names one of the methods anywhere in its constant pool. */
    static boolean mixinsMention(Class<?> target, String... names) {
        try {
            var info = org.spongepowered.asm.mixin.transformer.ClassInfo.fromCache(target.getName().replace('.', '/'));
            if (info == null) return false;
            for (var mixin : info.getAppliedMixins()) {
                String res = mixin.getClassName().replace('.', '/') + ".class";
                ClassLoader l = target.getClassLoader();
                try (InputStream in = l.getResourceAsStream(res)) {
                    if (in == null) return true;
                    String pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                    for (String n : names) if (pool.contains(n)) {
                        System.out.println("[probe/opt] entity collision skip disabled: " + mixin.getClassName() + " touches " + n);
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }
}
