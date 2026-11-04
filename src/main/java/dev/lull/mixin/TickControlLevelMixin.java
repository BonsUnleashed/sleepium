package dev.lull.mixin;

import dev.lull.TickControl;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TickControl: a frozen level ticks only its chunk source, without ticking chunks (1.21's frozen tick: no time, no
 * scheduled ticks, no entities or block entities; forced chunks keep loading).
 */
@Mixin(ServerLevel.class)
public abstract class TickControlLevelMixin {
    @Shadow @Final private net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity> entityManager;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sleep$frozen(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        if (!TickControl.levelFrozen()) return;
        ((ServerLevel) (Object) this).getChunkSource().tick(hasTimeLeft, false);
        entityManager.tick();   // as 1.21's frozen level: chunks' entity sections load and unload (a chunk counts as loaded then)
        ci.cancel();
    }
}
