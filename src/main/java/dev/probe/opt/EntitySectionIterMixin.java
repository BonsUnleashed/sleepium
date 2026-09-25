package dev.probe.opt;

import dev.probe.optsupport.IndexedSection;
import dev.probe.optsupport.OptCounters;
import dev.probe.optsupport.SectionIndex;
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
        if (INDEX && list.size() >= INDEX_MIN) {
            IndexedSection is = (IndexedSection) this;
            SectionIndex idx = is.probe$index();
            Thread t = Thread.currentThread();
            if (idx == null && is.probe$key() != Long.MIN_VALUE && t == serverThread()) {
                idx = new SectionIndex(is.probe$key());
                is.probe$setIndex(idx);
            }
            if (idx != null && idx.owner == t) {
                if (INDEX_CHECK) probe$check(idx, list, bounds);
                cir.setReturnValue(idx.query(list, bounds, consumer));
                return;
            }
        }
        for (int i = 0; i < list.size(); i++) {
            T t = list.get(i);
            if (t.getBoundingBox().intersects(bounds) && consumer.accept(t).shouldAbort()) {
                cir.setReturnValue(AbortableIterationConsumer.Continuation.ABORT);
                return;
            }
        }
        cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
    }

    private static final boolean INDEX = Boolean.getBoolean("lab.opt.EntitySectionIndexMixin");
    private static final boolean INDEX_CHECK = Boolean.getBoolean("lab.opt.sectionIndexCheck");
    private static final int INDEX_MIN = Integer.getInteger("lab.opt.sectionIndexMin", 48);
    private static Thread serverThread;

    private static Thread serverThread() {
        Thread t = serverThread;
        if (t == null) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) serverThread = t = server.getRunningThread();
        }
        return t;
    }

    /** Shadow check: the index must yield exactly the entities (and order) of the vanilla scan. */
    private void probe$check(SectionIndex idx, List<T> list, AABB bounds) {
        List<T> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) if (list.get(i).getBoundingBox().intersects(bounds)) a.add(list.get(i));
        idx.query(list, bounds, e -> {
            b.add(e);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        OptCounters.INDEX_CHECKED.incrementAndGet();
        if (!a.equals(b) && OptCounters.INDEX_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] section index MISMATCH: vanilla " + a.size() + " vs index " + b.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void probe$checkTyped(SectionIndex idx, List<T> list, AABB bounds, EntityTypeTest test) {
        List<Object> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object u = test.tryCast(list.get(i));
            if (u != null && list.get(i).getBoundingBox().intersects(bounds)) a.add(u);
        }
        idx.queryTyped(list, bounds, test, e -> {
            b.add(e);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        OptCounters.INDEX_CHECKED.incrementAndGet();
        if (!a.equals(b) && OptCounters.INDEX_MISMATCHES.incrementAndGet() <= 5)
            System.out.println("[probe/opt] typed section index MISMATCH: vanilla " + a.size() + " vs index " + b.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"), cancellable = true)
    private void probe$typed(EntityTypeTest test, AABB bounds, AbortableIterationConsumer consumer, CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        List<T> list = ((ClassInstanceMultiMapAccessor<T>) storage).probe$byClass().get(test.getBaseClass());
        if (list == null) return;   // first query for this class: vanilla find() builds the list
        if (INDEX && list.size() >= INDEX_MIN) {
            IndexedSection is = (IndexedSection) this;
            Class<?> base = test.getBaseClass();
            SectionIndex idx = is.probe$typedIndex(base);
            Thread th = Thread.currentThread();
            if (idx == null && is.probe$key() != Long.MIN_VALUE && th == serverThread()) {
                idx = new SectionIndex(is.probe$key());
                is.probe$putTypedIndex(base, idx);
            }
            if (idx != null && idx.owner == th) {
                if (INDEX_CHECK) probe$checkTyped(idx, list, bounds, test);
                cir.setReturnValue(idx.queryTyped(list, bounds, test, consumer));
                return;
            }
        }
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
