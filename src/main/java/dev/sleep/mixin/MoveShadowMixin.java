package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.MoveShadow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dprobe.moveShadow=true): MoveShadow around Entity.move. */
@Mixin(Entity.class)
public abstract class MoveShadowMixin {
    @Inject(method = "move", at = @At("HEAD"))
    private void probe$in(MoverType t, Vec3 v, CallbackInfo ci) { MoveShadow.before((Entity) (Object) this); }

    @Inject(method = "move", at = @At("RETURN"))
    private void probe$out(MoverType t, Vec3 v, CallbackInfo ci) { MoveShadow.after((Entity) (Object) this); }
}
