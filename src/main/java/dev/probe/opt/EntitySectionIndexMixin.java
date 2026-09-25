package dev.probe.opt;

import dev.probe.optsupport.IndexedSection;
import dev.probe.optsupport.SectionIndex;
import java.util.IdentityHashMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/section-index: exact spatial indices for dense entity sections (see SectionIndex): one over the section's
 * full list (untyped queries) and one per typed list (getEntitiesOfClass). This mixin stores the section key and the
 * indices, and marks them dirty on add/remove (list positions shift).
 */
@Mixin(EntitySection.class)
public abstract class EntitySectionIndexMixin<T extends EntityAccess> implements IndexedSection {
    @Unique private long probe$key = Long.MIN_VALUE;
    @Unique private SectionIndex probe$index;
    @Unique private IdentityHashMap<Class<?>, SectionIndex> probe$typed;

    @Override public long probe$key() { return probe$key; }
    @Override public void probe$setKey(long key) { probe$key = key; }
    @Override public SectionIndex probe$index() { return probe$index; }
    @Override public void probe$setIndex(SectionIndex index) { probe$index = index; }

    @Override
    public SectionIndex probe$typedIndex(Class<?> baseClass) {
        return probe$typed == null ? null : probe$typed.get(baseClass);
    }

    @Override
    public void probe$putTypedIndex(Class<?> baseClass, SectionIndex index) {
        if (probe$typed == null) probe$typed = new IdentityHashMap<>(4);
        probe$typed.put(baseClass, index);
    }

    @Override
    public void probe$forEachTyped(Object entity, net.minecraft.world.phys.AABB bb) {
        if (probe$typed == null) return;
        Thread t = Thread.currentThread();
        for (SectionIndex i : probe$typed.values()) if (i.owner == t) i.update(entity, bb);
    }

    @Unique
    private void probe$dirty() {
        if (probe$index != null) probe$index.markDirty();
        if (probe$typed != null) for (SectionIndex i : probe$typed.values()) i.markDirty();
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void probe$added(T entity, CallbackInfo ci) {
        probe$dirty();
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void probe$removed(T entity, CallbackInfoReturnable<Boolean> cir) {
        probe$dirty();
    }
}
