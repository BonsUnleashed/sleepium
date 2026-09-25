package dev.probe.opt;

import dev.probe.optsupport.IndexedSection;
import dev.probe.optsupport.SectionIndex;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity bounding boxes change only through setBoundingBox: keep the section index of the entity in sync. */
@Mixin(Entity.class)
public abstract class EntityBoxIndexMixin {
    @Shadow private EntityInLevelCallback levelCallback;

    @Inject(method = "setBoundingBox", at = @At("RETURN"))
    private void probe$box(AABB bb, CallbackInfo ci) {
        if (levelCallback instanceof EntityCallbackAccessor cb) {
            EntitySection<?> s = cb.probe$section();
            if (s != null) {
                IndexedSection is = (IndexedSection) s;
                SectionIndex idx = is.probe$index();
                if (idx != null && idx.owner == Thread.currentThread()) idx.update(this, bb);
                is.probe$forEachTyped(this, bb);
            }
        }
    }
}
