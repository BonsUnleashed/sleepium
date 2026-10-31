package dev.sleep.opt;

import dev.sleep.optsupport.JobSiteBrainAccess;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.JobSiteIndex: refreshBrain replaces the villager's brain by a copy; the old one gives back its job site. */
@Mixin(Villager.class)
public abstract class JobSiteRefreshMixin {
    @Inject(method = "refreshBrain", at = @At("HEAD"))
    private void probe$refresh(ServerLevel level, CallbackInfo ci) {
        ((JobSiteBrainAccess) ((Villager) (Object) this).getBrain()).probe$release();
    }
}
