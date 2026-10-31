package dev.sleep.geomixin;

import dev.sleep.optsupport.ParallelRender;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.renderer.specialty.DynamicGeoEntityRenderer;

/**
 * DynamicGeoEntityRenderer's texture size cache is a static HashMap filled lazily, and the fill waits for the render
 * thread (mc.submit(...).get(): a deadlock on a worker while the render thread waits for the workers). Guarded by the
 * map's lock; a miss inside a ParallelRender job hands the job to the render thread, which fills it.
 */
@Mixin(value = DynamicGeoEntityRenderer.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class GeoTextureDimsMixin {
    @Redirect(method = "computeTextureSize", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object sleep$dims(Map map, Object key, Function fill) {
        synchronized (map) {
            Object v = map.get(key);
            if (v != null) return v;
            if (ParallelRender.inJob()) throw ParallelRender.REFUSE_NOT_READY;
            v = fill.apply(key);
            map.put(key, v);
            return v;
        }
    }
}
