package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import dev.lull.GenProfile;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measurement only (genbench): the synchronous body of every generation step ("step:<status>"; 1.20.1: ChunkStatus's
 * generate). NOISE and BIOMES only hand off here; their work is timed in GenProfileNoiseMixin, light in
 * GenProfileLightMixin. With ParallelWorldgenMixin the outer call only hands the step to the pool: the re-entrant call
 * there is the one timed.
 */
@Mixin(ChunkStatus.class)
public abstract class GenProfileMixin {
    @WrapMethod(method = "generate")
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> sleep$time(Executor executor, ServerLevel level, ChunkGenerator generator,
            StructureTemplateManager templates, ThreadedLevelLightEngine light,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> task, List<ChunkAccess> cache,
            Operation<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> original) {
        ChunkStatus status = (ChunkStatus) (Object) this;
        if (!GenProfile.active() || dev.lull.optsupport.ParallelGen.offloads(status)) return original.call(executor, level, generator, templates, light, task, cache);
        long t = System.nanoTime(), m = GenProfile.allocated(), pn = GenProfile.perlin();
        try {
            return original.call(executor, level, generator, templates, light, task, cache);
        } finally {
            GenProfile.add("step:" + net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(status).getPath(), System.nanoTime() - t, GenProfile.allocated() - m, GenProfile.perlin() - pn);
        }
    }
}
