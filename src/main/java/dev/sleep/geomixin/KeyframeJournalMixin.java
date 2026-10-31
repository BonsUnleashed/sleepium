package dev.sleep.geomixin;

import dev.sleep.optsupport.ParallelRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;

/**
 * Keyframe events (sounds, particles, custom instructions: a mod's code that touches the level, the sound engine...)
 * reached inside a ParallelRender job: kept in the job and run on the render thread when the job is merged, in order.
 */
@Mixin(value = AnimationController.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class KeyframeJournalMixin {
    @Redirect(method = "processCurrentAnimation", at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/animation/AnimationController$SoundKeyframeHandler;handle(Lsoftware/bernie/geckolib/animation/keyframe/event/SoundKeyframeEvent;)V"))
    private void probe$sound(AnimationController.SoundKeyframeHandler h, SoundKeyframeEvent e) {
        if (!ParallelRender.journal(() -> h.handle(e))) h.handle(e);
    }

    @Redirect(method = "processCurrentAnimation", at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/animation/AnimationController$ParticleKeyframeHandler;handle(Lsoftware/bernie/geckolib/animation/keyframe/event/ParticleKeyframeEvent;)V"))
    private void probe$particle(AnimationController.ParticleKeyframeHandler h, ParticleKeyframeEvent e) {
        if (!ParallelRender.journal(() -> h.handle(e))) h.handle(e);
    }

    @Redirect(method = "processCurrentAnimation", at = @At(value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/animation/AnimationController$CustomKeyframeHandler;handle(Lsoftware/bernie/geckolib/animation/keyframe/event/CustomInstructionKeyframeEvent;)V"))
    private void probe$custom(AnimationController.CustomKeyframeHandler h, CustomInstructionKeyframeEvent e) {
        if (!ParallelRender.journal(() -> h.handle(e))) h.handle(e);
    }
}
