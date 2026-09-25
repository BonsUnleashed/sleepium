package dev.probe.opt;

import java.util.List;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/section-iter (goes with EntitySectionScanMixin): iterate a section's entities with an index loop over the
 * same backing lists, in the same order. Vanilla allocates per call: two iterators (unmodifiable wrapper + list
 * iterator) for untyped queries; for typed queries a capturing computeIfAbsent lambda, an unmodifiable collection
 * wrapper and its iterators. The typed list is still created by vanilla's own find() the first time.
 */
@Mixin(EntitySection.class)
public abstract class EntitySectionIterMixin<T extends EntityAccess> {
    @Shadow @Final private ClassInstanceMultiMap<T> storage;

    @SuppressWarnings("unchecked")
    @Inject(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"), cancellable = true)
    private void probe$all(AABB bounds, AbortableIterationConsumer<T> consumer, CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        List<T> list = ((ClassInstanceMultiMapAccessor<T>) storage).probe$all();
        for (int i = 0; i < list.size(); i++) {
            T t = list.get(i);
            if (t.getBoundingBox().intersects(bounds) && consumer.accept(t).shouldAbort()) {
                cir.setReturnValue(AbortableIterationConsumer.Continuation.ABORT);
                return;
            }
        }
        cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"), cancellable = true)
    private void probe$typed(EntityTypeTest test, AABB bounds, AbortableIterationConsumer consumer, CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        List<T> list = ((ClassInstanceMultiMapAccessor<T>) storage).probe$byClass().get(test.getBaseClass());
        if (list == null) return;   // first query for this class: vanilla find() builds the list
        for (int i = 0; i < list.size(); i++) {
            T t = list.get(i);
            Object u = test.tryCast(t);
            if (u != null && t.getBoundingBox().intersects(bounds) && consumer.accept(u).shouldAbort()) {
                cir.setReturnValue(AbortableIterationConsumer.Continuation.ABORT);
                return;
            }
        }
        cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
    }
}
