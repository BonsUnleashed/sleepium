package dev.lull.optsupport;

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
    // 1.20.1: Forge production runs SRG member names: reflection asks for the running names (1.21: Mojang's)
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String CAN_BE_COLLIDED = dev.lull.Names.method(ENTITY, "canBeCollidedWith", "()Z");
    private static final String CAN_COLLIDE = dev.lull.Names.method(ENTITY, "canCollideWith", "(Lnet/minecraft/world/entity/Entity;)Z");

    public static final ClassValue<Boolean> CAPABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod(CAN_BE_COLLIDED).getDeclaringClass() != Entity.class;
            } catch (Throwable t) {
                return true;
            }
        }
    };

    public static final ClassValue<Boolean> DEFAULT_COLLIDE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod(CAN_COLLIDE, Entity.class).getDeclaringClass() == Entity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    // MixinTouch under both names (injector targets as written, Mojang's; merged / overwritten methods of reobfuscated
    // mixins: SRG)
    public static final boolean ENTITY_UNTOUCHED = !MixinTouch.touched(ENTITY, "canBeCollidedWith") && !MixinTouch.touched(ENTITY, CAN_BE_COLLIDED)
            && !MixinTouch.touched(ENTITY, "canCollideWith") && !MixinTouch.touched(ENTITY, CAN_COLLIDE);

    public static volatile long skipped, full;
    /** moved on whenever a collidable entity joins or leaves an entity section (any level, any thread): MoveMemo */
    public static final java.util.concurrent.atomic.AtomicLong COLLIDABLE_EPOCH = new java.util.concurrent.atomic.AtomicLong();

    private EntityCollide() {}

}
