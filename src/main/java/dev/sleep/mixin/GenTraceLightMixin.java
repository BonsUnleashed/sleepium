package dev.sleep.mixin;

import dev.sleep.optsupport.GenTrace;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dlab.pgen.trace=true): light storage snapshots - how often the whole section map is copied, how big. */
@Mixin(LayerLightSectionStorage.class)
public abstract class GenTraceLightMixin<M extends DataLayerStorageMap<M>> {
    @Shadow @Final protected M updatingSectionData;
    @Shadow @Final protected LongSet changedSections;

    @Inject(method = "swapSectionMap", at = @At("HEAD"))
    private void sleep$swap(CallbackInfo ci) {
        if (!changedSections.isEmpty()) GenTrace.lightCopy(((DataLayerMapAccessor) (Object) updatingSectionData).sleep$map().size(), changedSections.size());
    }
}
