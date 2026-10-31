package dev.sleep.optsupport;

import net.minecraft.world.phys.AABB;

/** EntitySection: how many of its entities may return true from canBeCollidedWith (EntityCollide.CAPABLE). */
public interface CollidableCount {
    int sleep$collidable();

    /** EntitySectionStorage: does any section vanilla's walk would visit for {@code bb} hold such an entity? */
    interface Storage {
        boolean sleep$anyCollidable(AABB bb);
    }
}
