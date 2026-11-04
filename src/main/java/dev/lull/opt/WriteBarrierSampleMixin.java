package dev.lull.opt;

import dev.lull.optsupport.sleep.WB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The write barrier's sampling (-Dlab.wb.sample=N): the window of one entity's tick. On 1.21.1 NeoForge's
 * EntityTickEvent.Pre / Post mark it (fired around {@code entity.tick()} in ServerLevel.tickNonPassenger); Forge 1.20.1
 * has no such event pair, so the same two points are hooked here.
 */
@Mixin(ServerLevel.class)
public abstract class WriteBarrierSampleMixin {
    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void sleep$pre(Entity entity, CallbackInfo ci) {
        WB.onPre(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void sleep$post(Entity entity, CallbackInfo ci) {
        WB.onPost(entity);
    }
}
