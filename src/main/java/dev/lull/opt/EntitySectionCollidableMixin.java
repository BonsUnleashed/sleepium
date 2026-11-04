package dev.lull.opt;

import dev.lull.optsupport.CollidableCount;
import dev.lull.optsupport.EntityCollide;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Goes with EntityCollisionSkipMixin: counts the section's entities whose class may be collided with. */
@Mixin(EntitySection.class)
public abstract class EntitySectionCollidableMixin implements CollidableCount {
    @Unique private int sleep$collidable;

    @Override
    public int sleep$collidable() {
        return sleep$collidable;
    }

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInstanceMultiMap;add(Ljava/lang/Object;)Z"))
    private boolean sleep$add(ClassInstanceMultiMap<Object> storage, Object e) {
        boolean r = storage.add(e);
        if (r && EntityCollide.CAPABLE.get(e.getClass())) {
            sleep$collidable++;
            EntityCollide.COLLIDABLE_EPOCH.incrementAndGet();
        }
        return r;
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInstanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private boolean sleep$remove(ClassInstanceMultiMap<Object> storage, Object e) {
        boolean r = storage.remove(e);
        if (r && EntityCollide.CAPABLE.get(e.getClass())) {
            sleep$collidable--;
            EntityCollide.COLLIDABLE_EPOCH.incrementAndGet();
        }
        return r;
    }
}
