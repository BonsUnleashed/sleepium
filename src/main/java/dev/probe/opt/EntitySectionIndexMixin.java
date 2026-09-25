package dev.probe.opt;

import dev.probe.optsupport.IndexedSection;
import dev.probe.optsupport.SectionIndex;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/section-index: exact spatial index for dense entity sections (see SectionIndex). This mixin stores the
 * section key and the index, and marks the index dirty on add/remove (list positions shift).
 */
@Mixin(EntitySection.class)
public abstract class EntitySectionIndexMixin<T extends EntityAccess> implements IndexedSection {
    @Unique private long probe$key = Long.MIN_VALUE;
    @Unique private SectionIndex probe$index;

    @Override public long probe$key() { return probe$key; }
    @Override public void probe$setKey(long key) { probe$key = key; }
    @Override public SectionIndex probe$index() { return probe$index; }
    @Override public void probe$setIndex(SectionIndex index) { probe$index = index; }

    @Inject(method = "add", at = @At("RETURN"))
    private void probe$added(T entity, CallbackInfo ci) {
        if (probe$index != null) probe$index.markDirty();
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void probe$removed(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (probe$index != null) probe$index.markDirty();
    }
}
