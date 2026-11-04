package dev.lull.opt;

import com.mojang.datafixers.util.Either;
import dev.lull.optsupport.ParallelGen;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * exp/parallel-worldgen: hand SURFACE/CARVERS/FEATURES to ParallelGen's pool instead of running them inside the
 * single worldgen mailbox actor. 1.20.1: ChunkMap.scheduleChunkGeneration's compose (on the worldgen mailbox) calls
 * ChunkStatus.generate; the returned future lets the actor go on with the next chunk's task (as NOISE's background
 * future does), so many chunks progress at once. The re-entrant call on the pool is vanilla's generate (its status
 * update, its profiler duration).
 */
@Mixin(ChunkStatus.class)
public abstract class ParallelWorldgenMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void sleep$offload(Executor executor, ServerLevel level, ChunkGenerator generator, StructureTemplateManager templates, ThreadedLevelLightEngine light,
                               Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> task, List<ChunkAccess> cache,
                               CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        ChunkStatus self = (ChunkStatus) (Object) this;
        if (ParallelGen.shouldOffload(self)) {
            ChunkPos pos = cache.get(cache.size() / 2).getPos();
            cir.setReturnValue(ParallelGen.submit(pos, self, () -> {
                try {
                    return self.generate(executor, level, generator, templates, light, task, cache);
                } catch (RuntimeException | Error e) {
                    throw ParallelGen.crash(e, level, generator, pos);   // vanilla's report, outside its try (see ParallelGen)
                }
            }));
        }
    }
}
