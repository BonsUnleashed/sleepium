package dev.sleep.opt;

import dev.sleep.optsupport.sleep.MoveMemo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** lab.opt.MoveMemo: the vanilla readers of tickCount on move's step-sound path mark their runs (MoveMemo.INSTRUMENTED). */
@Mixin(Entity.class)
public abstract class MoveMemoReadersMixin {
    @Inject(method = "shouldPlayAmethystStepSound", at = @At("HEAD"))
    private void probe$should(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        MoveMemo.readerRan((Entity) (Object) this);
    }

    @Inject(method = "playAmethystStepSound", at = @At("HEAD"))
    private void probe$play(CallbackInfo ci) {
        MoveMemo.readerRan((Entity) (Object) this);
    }
}
