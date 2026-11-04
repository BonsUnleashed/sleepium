package dev.lull.opt;

import dev.lull.optsupport.JobSiteBrainAccess;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** lab.opt.JobSiteIndex: a brain replaced by one read from NBT gives back its job site. */
@Mixin(LivingEntity.class)
public abstract class JobSiteReplaceMixin {
    @Shadow protected Brain<?> brain;

    @Inject(method = "readAdditionalSaveData", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;brain:Lnet/minecraft/world/entity/ai/Brain;", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void sleep$replaced(CompoundTag tag, CallbackInfo ci) {
        if (brain != null) ((JobSiteBrainAccess) brain).sleep$release();
    }
}
