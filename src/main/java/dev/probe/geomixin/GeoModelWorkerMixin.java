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
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelWorkerMixin {
    @Unique private boolean probe$worker;
    @Unique private BakedGeoModel probe$orig, probe$copy;

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
        if (model != probe$orig) {   // first use, or a resource reload baked the model again
            probe$copy = GeoParallel.copy((BakedGeoModel) model);
            probe$orig = (BakedGeoModel) model;
        }
        return probe$copy;
    }
}
