package dev.sleep.opt;

import dev.sleep.optsupport.sleep.MoveMemo;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.MoveMemo: a modification counter on AttributeMap (every AttributeInstance change reports here). */
@Mixin(AttributeMap.class)
public abstract class MoveMemoAttributesMixin implements MoveMemo.AttrEpoch {
    @Unique private int probe$attrEpoch;

    @Override
    public int probe$attrEpoch() {
        return probe$attrEpoch;
    }

    @Inject(method = "onAttributeModified", at = @At("HEAD"))
    private void probe$modified(AttributeInstance instance, CallbackInfo ci) {
        probe$attrEpoch++;
    }
}
