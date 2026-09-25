package dev.probe.optsupport;

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

    public static final boolean ENTITY_UNTOUCHED = !MixinTouch.touched("net/minecraft/world/entity/Entity", "canBeCollidedWith")
            && !MixinTouch.touched("net/minecraft/world/entity/Entity", "canCollideWith");

    public static volatile long skipped, full;

    private EntityCollide() {}

}
