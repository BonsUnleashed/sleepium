package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.lull.GenProfile;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Measurement only (genbench): time the worldgen actor spends in its tasks (1.20.1: the `worldgen` ProcessorMailbox's
 * pollTask - scheduleChunkGeneration's composes, i.e. scheduling plus every step body that is not handed off; 1.21.1
 * timed ChunkGenerationTask.runUntilWait). Its sum over the run, divided by the run time, is the actor's load: 1.0 =
 * the single actor is saturated.
 */
@Mixin(ProcessorMailbox.class)
public abstract class GenProfileTaskMixin {
    @Shadow @Final private String name;

    @WrapMethod(method = "pollTask")
    private boolean sleep$time(Operation<Boolean> original) {
        if (!GenProfile.active() || !"worldgen".equals(this.name)) return original.call();
        long t = System.nanoTime(), m = GenProfile.allocated();
        boolean ran = false;
        try {
            return ran = original.call();
        } finally {
            if (ran) GenProfile.add("actor: worldgen task", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
