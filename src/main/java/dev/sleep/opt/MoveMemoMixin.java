package dev.sleep.opt;

import dev.sleep.optsupport.sleep.MoveMemo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.MoveMemo: an unchanged move of a living entity skipped (MoveMemo). */
@Mixin(Entity.class)
public abstract class MoveMemoMixin implements MoveMemo.Holder {
    @Unique private Object sleep$moveMemo;

    @Override
    public Object sleep$moveMemo() {
        return sleep$moveMemo;
    }

    @Override
    public void sleep$setMoveMemo(Object s) {
        sleep$moveMemo = s;
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void sleep$memo(MoverType type, Vec3 v, CallbackInfo ci) {
        if (MoveMemo.before((Entity) (Object) this, type, v)) ci.cancel();
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void sleep$record(MoverType type, Vec3 v, CallbackInfo ci) {
        MoveMemo.after((Entity) (Object) this, v);
    }
}
