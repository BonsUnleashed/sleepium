package dev.probe.geomixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.probe.optsupport.GeoParallel;
import dev.probe.optsupport.ParallelRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

/**
 * A GeoModel made while ParallelRender builds its worker renderer sets animates its own copy of the baked model (own
 * bones); GeoModel then registers the copy's bones with its processor as it would the cached model's. A GeoModel not
 * made for a worker set (the render thread's, or one a mod keeps in a static) used inside a worker job: refused, that
 * entity type stays on the render thread.
 * <p>
 * A copy per baked model the GeoModel is asked for (10-08): a model class that draws each entity with a model of its own
 * (a game's per-entity appearance: one baked model a face) had one copy slot - every switch between entities made a
 * whole new copy, hundreds a second with many faces on screen (and anything that remembered the bones kept them all:
 * the heap filled). Copies kept per model, by identity; forgotten when there are more than 512 (a resource reload).
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelWorkerMixin {
    @Unique private boolean probe$worker;
    @Unique private java.util.IdentityHashMap<BakedGeoModel, BakedGeoModel> probe$copies;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void probe$mark(CallbackInfo ci) {
        probe$worker = ParallelRender.buildingSets;
    }

    @ModifyExpressionValue(method = "getBakedModel", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object probe$workerCopy(Object model) {
        if (!probe$worker) {
            if (model != null && ParallelRender.inJob()) throw ParallelRender.REFUSE_SHARED;
            return model;
        }
        if (model == null) return null;
        if (probe$copies == null) probe$copies = new java.util.IdentityHashMap<>();
        BakedGeoModel copy = probe$copies.get(model);
        if (copy == null) {   // first use of this model (or a resource reload baked it again)
            if (probe$copies.size() >= 512) probe$copies.clear();
            copy = GeoParallel.copy((BakedGeoModel) model);
            probe$copies.put((BakedGeoModel) model, copy);
        }
        return copy;
    }
}
