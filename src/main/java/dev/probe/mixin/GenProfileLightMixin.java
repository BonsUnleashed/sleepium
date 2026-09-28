package dev.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.probe.GenProfile;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): one batch of the light actor (pre tasks, propagation, post tasks). */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class GenProfileLightMixin {
    @WrapMethod(method = "runUpdate")
    private void probe$light(Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call();
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call();
        } finally {
            GenProfile.add("light.runUpdate", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
