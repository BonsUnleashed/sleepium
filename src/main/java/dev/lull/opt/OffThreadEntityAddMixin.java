package dev.lull.opt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An entity added to the live level from another thread goes where it belongs (dev.lull.optsupport.OffThreadEntity). */
@Mixin(ServerLevel.class)
public abstract class OffThreadEntityAddMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void sleep$offThread(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (self.getServer().isSameThread()) return;   // every correct caller
        cir.setReturnValue(dev.lull.optsupport.OffThreadEntity.add(self, entity));
    }
}
