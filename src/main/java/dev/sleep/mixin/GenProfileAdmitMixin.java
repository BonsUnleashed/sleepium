package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): the main-thread parts of a chunk entering the world — inside FULL (post-load: entities,
 * block entity registration, tick containers) and when it starts ticking (post-processing: fluid ticks, shape updates).
 */
@Mixin(LevelChunk.class)
public abstract class GenProfileAdmitMixin {
    @WrapMethod(method = "runPostLoad")
    private void sleep$postLoad(Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call();
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call();
        } finally {
            GenProfile.add("main: (inside FULL) runPostLoad", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapMethod(method = "registerAllBlockEntitiesAfterLevelLoad")
    private void sleep$bes(Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call();
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call();
        } finally {
            GenProfile.add("main: (inside FULL) register block entities", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapMethod(method = "registerTickContainerInLevel")
    private void sleep$ticks(ServerLevel level, Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call(level);
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call(level);
        } finally {
            GenProfile.add("main: (inside FULL) register tick containers", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }

    @WrapMethod(method = "postProcessGeneration")
    private void sleep$post(Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call();
            return;
        }
        for (it.unimi.dsi.fastutil.shorts.ShortList l : ((LevelChunk) (Object) this).getPostProcessing())
            if (l != null) for (int i = 0; i < l.size(); i++) GenProfile.add("main: post-processing marks", 0, 0);
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call();
        } finally {
            GenProfile.add("main: postProcessGeneration", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
