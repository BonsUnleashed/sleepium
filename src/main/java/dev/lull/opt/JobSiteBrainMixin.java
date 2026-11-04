package dev.lull.opt;

import dev.lull.optsupport.JobSiteBrainAccess;
import dev.lull.optsupport.JobSiteIndex;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lab.opt.JobSiteIndex: the brain's JOB_SITE memory followed into JobSiteIndex after every write of it (set, erase,
 * expiry and load all go through setMemoryInternal; clearMemories; copyWithoutBehaviors puts the copy's memories
 * directly). {@link #sleep$release} when the brain stops being used.
 */
@Mixin(Brain.class)
public abstract class JobSiteBrainMixin implements JobSiteBrainAccess {
    @Shadow @Final private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;
    @Unique private BlockPos sleep$jobSite;
    @Unique private boolean sleep$released;

    @Override
    public void sleep$follow() {
        if (sleep$released) return;
        Optional<? extends ExpirableValue<?>> v = memories.get(MemoryModuleType.JOB_SITE);
        BlockPos now = v != null && v.isPresent() && v.get().getValue() instanceof GlobalPos g ? g.pos() : null;
        if (now != sleep$jobSite) sleep$jobSite = JobSiteIndex.update(sleep$jobSite, now);
    }

    @Inject(method = "setMemoryInternal", at = @At("RETURN"))
    private void sleep$set(MemoryModuleType<?> type, Optional<? extends ExpirableValue<?>> memory, CallbackInfo ci) {
        if (type == MemoryModuleType.JOB_SITE) sleep$follow();
    }

    @Inject(method = "clearMemories", at = @At("RETURN"))
    private void sleep$clear(CallbackInfo ci) {
        sleep$follow();
    }

    @Inject(method = "copyWithoutBehaviors", at = @At("RETURN"))
    private void sleep$copy(CallbackInfoReturnable<Brain<?>> cir) {
        ((JobSiteBrainAccess) cir.getReturnValue()).sleep$follow();
    }

    @Override
    public void sleep$release() {
        if (sleep$released) return;
        sleep$jobSite = JobSiteIndex.update(sleep$jobSite, null);
        sleep$released = true;
    }
}
