package dev.sleep.opt;

import com.llamalad7.mixinextras.sugar.Local;
import dev.sleep.optsupport.ChunkTickMemo;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** exp/chunk-tick-memo: see ChunkTickMemo. */
@Mixin(ServerChunkCache.class)
public abstract class ChunkTickMemoMixin {
    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean probe$spawningAllowed(ServerLevel level, ChunkPos pos, @Local LevelChunk chunk) {
        ChunkTickMemo.Holder h = (ChunkTickMemo.Holder) chunk;
        int e = ChunkTickMemo.epoch;
        if (h.probe$visEpoch() == e) {
            ChunkTickMemo.hits++;
            return h.probe$vis();
        }
        boolean v = level.isNaturalSpawningAllowed(pos);
        h.probe$setVis(e, v);
        ChunkTickMemo.misses++;
        return v;
    }
}
