package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkGenerationTask;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): time the worldgen actor spends in ChunkGenerationTask.runUntilWait (scheduling the
 * pyramid plus every step body that is not handed off). Its sum over the run, divided by the run time, is the actor's
 * load: 1.0 = the single actor is saturated.
 */
@Mixin(ChunkGenerationTask.class)
public abstract class GenProfileTaskMixin {
    @WrapMethod(method = "runUntilWait")
    private CompletableFuture<?> sleep$time(Operation<CompletableFuture<?>> original) {
        if (!GenProfile.active()) return original.call();
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            return original.call();
        } finally {
            GenProfile.add("actor: runUntilWait", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
