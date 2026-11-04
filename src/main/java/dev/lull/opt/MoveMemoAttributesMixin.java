package dev.lull.opt;

import dev.lull.optsupport.sleep.MoveMemo;
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
    @Unique private int sleep$attrEpoch;

    @Override
    public int sleep$attrEpoch() {
        return sleep$attrEpoch;
    }

    @Inject(method = "onAttributeModified", at = @At("HEAD"))
    private void sleep$modified(AttributeInstance instance, CallbackInfo ci) {
        sleep$attrEpoch++;
    }
}
