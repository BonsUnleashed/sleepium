package dev.probe.opt;

import dev.probe.optsupport.JobSiteBrainAccess;
import dev.probe.optsupport.JobSiteIndex;
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
 * directly). {@link #probe$release} when the brain stops being used.
 */
@Mixin(Brain.class)
public abstract class JobSiteBrainMixin implements JobSiteBrainAccess {
    @Shadow @Final private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;
    @Unique private BlockPos probe$jobSite;
    @Unique private boolean probe$released;

    @Override
    public void probe$follow() {
        if (probe$released) return;
        Optional<? extends ExpirableValue<?>> v = memories.get(MemoryModuleType.JOB_SITE);
        BlockPos now = v != null && v.isPresent() && v.get().getValue() instanceof GlobalPos g ? g.pos() : null;
        if (now != probe$jobSite) probe$jobSite = JobSiteIndex.update(probe$jobSite, now);
    }

    @Inject(method = "setMemoryInternal", at = @At("RETURN"))
    private void probe$set(MemoryModuleType<?> type, Optional<? extends ExpirableValue<?>> memory, CallbackInfo ci) {
        if (type == MemoryModuleType.JOB_SITE) probe$follow();
    }

    @Inject(method = "clearMemories", at = @At("RETURN"))
    private void probe$clear(CallbackInfo ci) {
        probe$follow();
    }

    @Inject(method = "copyWithoutBehaviors", at = @At("RETURN"))
    private void probe$copy(CallbackInfoReturnable<Brain<?>> cir) {
        ((JobSiteBrainAccess) cir.getReturnValue()).probe$follow();
    }

    @Override
    public void probe$release() {
        if (probe$released) return;
        probe$jobSite = JobSiteIndex.update(probe$jobSite, null);
        probe$released = true;
    }
}
