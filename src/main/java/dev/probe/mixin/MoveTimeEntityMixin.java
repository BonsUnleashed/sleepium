package dev.probe.mixin;

import dev.probe.optsupport.MoveTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (-Dprobe.moveTime=true): MoveTime's Entity parts. */
@Mixin(Entity.class)
public abstract class MoveTimeEntityMixin {
    @Inject(method = "move", at = @At("HEAD"))
    private void probe$moveIn(MoverType t, Vec3 v, CallbackInfo ci) { MoveTime.enter(MoveTime.MOVE, (Entity) (Object) this); }

    @Inject(method = "move", at = @At("RETURN"))
    private void probe$moveOut(MoverType t, Vec3 v, CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "collide", at = @At("HEAD"))
    private void probe$collideIn(Vec3 v, CallbackInfoReturnable<Vec3> ci) { MoveTime.enter(MoveTime.COLLIDE, (Entity) (Object) this); }

    @Inject(method = "collide", at = @At("RETURN"))
    private void probe$collideOut(Vec3 v, CallbackInfoReturnable<Vec3> ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "checkInsideBlocks", at = @At("HEAD"))
    private void probe$insideIn(CallbackInfo ci) { MoveTime.enter(MoveTime.INSIDE, (Entity) (Object) this); }

    @Inject(method = "checkInsideBlocks", at = @At("RETURN"))
    private void probe$insideOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("HEAD"), remap = false)
    private void probe$fluidIn(CallbackInfo ci) { MoveTime.enter(MoveTime.FLUIDS, (Entity) (Object) this); }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("RETURN"), remap = false)
    private void probe$fluidOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "checkSupportingBlock", at = @At("HEAD"))
    private void probe$supIn(boolean g, Vec3 v, CallbackInfo ci) { MoveTime.enter(MoveTime.SUPPORT, (Entity) (Object) this); }

    @Inject(method = "checkSupportingBlock", at = @At("RETURN"))
    private void probe$supOut(boolean g, Vec3 v, CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"))
    private void probe$speedIn(CallbackInfoReturnable<Float> ci) { MoveTime.enter(MoveTime.SPEED, (Entity) (Object) this); }

    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"))
    private void probe$speedOut(CallbackInfoReturnable<Float> ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "checkFallDamage", at = @At("HEAD"))
    private void probe$fallIn(double y, boolean g, net.minecraft.world.level.block.state.BlockState st, net.minecraft.core.BlockPos p, CallbackInfo ci) { MoveTime.enter(MoveTime.FALL, (Entity) (Object) this); }

    @Inject(method = "checkFallDamage", at = @At("RETURN"))
    private void probe$fallOut(double y, boolean g, net.minecraft.world.level.block.state.BlockState st, net.minecraft.core.BlockPos p, CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockStatesIfLoaded(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private void probe$fireIn(MoverType t, Vec3 v, CallbackInfo ci) { MoveTime.enter(MoveTime.FIRE, (Entity) (Object) this); }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;noneMatch(Ljava/util/function/Predicate;)Z", shift = At.Shift.AFTER))
    private void probe$fireOut(MoverType t, Vec3 v, CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void probe$baseIn(CallbackInfo ci) { MoveTime.enter(MoveTime.BASE, (Entity) (Object) this); }

    @Inject(method = "baseTick", at = @At("RETURN"))
    private void probe$baseOut(CallbackInfo ci) { MoveTime.exit((Entity) (Object) this); }
}
